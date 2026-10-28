package com.aechak.infra.client.payment

import com.aechak.application.payment.port.PaymentWebhookNotification
import com.aechak.application.payment.port.PaymentWebhookNotificationType
import com.aechak.application.payment.port.PaymentWebhookRequest
import com.aechak.application.payment.port.PaymentWebhookVerifier
import com.aechak.common.error.BusinessException
import com.aechak.domain.payment.error.PaymentErrorCode
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import java.security.MessageDigest
import java.time.Instant
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.abs

/**
 * 포트원 웹훅 검증기. 서명 규격은 Standard Webhooks —
 * "{webhook-id}.{webhook-timestamp}.{본문}"을 시크릿으로 HMAC-SHA256 한 값이 헤더의 서명 목록에 있어야 한다.
 * 본문은 받은 문자열 그대로 써야 한다. 파싱했다 다시 만든 JSON은 공백 한 칸 차이로도 서명이 어긋난다.
 */
@Component
class PortOneWebhookVerifier(
    private val properties: PortOneProperties,
) : PaymentWebhookVerifier {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun verify(request: PaymentWebhookRequest): PaymentWebhookNotification? {
        val id = request.id ?: reject("webhook-id 헤더 없음")
        val timestamp = request.timestamp ?: reject("webhook-timestamp 헤더 없음")
        val signature = request.signature ?: reject("webhook-signature 헤더 없음")

        verifyFreshness(timestamp)
        // 시크릿 로딩은 요청 형식 검사 뒤 — 형식도 안 갖춘 발신자에게 응답 차이로 설정 상태를 흘리지 않는다
        verifySignature(secretKey(), id, timestamp, request.rawBody, signature)
        return parse(request.rawBody)
    }

    /** 시크릿은 whsec_ 접두 뒤 Base64. 미설정이면 진위를 가릴 수단이 없으므로 받지 않는다 */
    private fun secretKey(): ByteArray {
        val secret = properties.webhookSecret
        if (secret.isBlank()) {
            log.error("포트원 웹훅 시크릿이 설정되지 않아 검증을 중단함")
            throw BusinessException(PaymentErrorCode.PAYMENT_GATEWAY_ERROR)
        }
        return runCatching { Base64.getDecoder().decode(secret.removePrefix(SECRET_PREFIX)) }
            .getOrElse {
                log.error("포트원 웹훅 시크릿이 Base64가 아님 — 설정 확인 필요")
                throw BusinessException(PaymentErrorCode.PAYMENT_GATEWAY_ERROR)
            }
    }

    /** 가로챈 요청을 나중에 그대로 다시 보내는 재생 공격 차단 — 서명은 그대로라 시각으로만 걸러진다 */
    private fun verifyFreshness(timestamp: String) {
        val sentAtEpochSecond = timestamp.toLongOrNull() ?: reject("webhook-timestamp가 숫자가 아님")
        val gapSeconds = abs(Instant.now().epochSecond - sentAtEpochSecond)
        if (gapSeconds > TOLERANCE_SECONDS) reject("허용 시각을 벗어남(${gapSeconds}초)")
    }

    private fun verifySignature(
        key: ByteArray,
        id: String,
        timestamp: String,
        rawBody: String,
        header: String,
    ) {
        val expected = hmacBase64(key, "$id.$timestamp.$rawBody").toByteArray()
        // 헤더엔 "v1,서명"이 공백으로 여러 개 올 수 있다(시크릿 교체 중). 하나라도 맞으면 통과
        val matched =
            header.split(" ").any { entry ->
                entry.substringBefore(",", "") == SIGNATURE_VERSION &&
                    MessageDigest.isEqual(expected, entry.substringAfter(",", "").toByteArray())
            }
        if (!matched) reject("서명 불일치")
    }

    private fun hmacBase64(
        key: ByteArray,
        signedContent: String,
    ): String {
        val mac = Mac.getInstance(HMAC_ALGORITHM)
        mac.init(SecretKeySpec(key, HMAC_ALGORITHM))
        return Base64.getEncoder().encodeToString(mac.doFinal(signedContent.toByteArray()))
    }

    /** 확정 흐름을 태울 사건만 남긴다. 나머지(가상계좌 발급·취소 통보 등)는 태울 이유가 없어 null로 접는다 */
    private fun parse(rawBody: String): PaymentWebhookNotification? {
        val root = runCatching { BODY_MAPPER.readTree(rawBody) }.getOrElse { reject("본문이 JSON이 아님") }
        val type = runCatching { root.path("type").stringValue() }.getOrNull()
        val eventType = type?.let { EVENT_TYPES[it] }
        if (eventType == null) {
            log.debug("확정과 무관한 웹훅이라 넘김. type={}", type)
            return null
        }
        val paymentId = runCatching { root.path("data").path("paymentId").stringValue() }.getOrNull()
        if (paymentId.isNullOrBlank()) {
            log.warn("결제 식별자가 없는 웹훅이라 넘김. type={}", type)
            return null
        }
        return PaymentWebhookNotification(paymentId = paymentId, type = eventType)
    }

    /** 검증 실패는 전부 같은 응답으로 나간다 — 어디서 틀렸는지는 발신자가 아니라 로그가 알아야 한다 */
    private fun reject(reason: String): Nothing {
        log.warn("포트원 웹훅 검증 실패. reason={}", reason)
        throw BusinessException(PaymentErrorCode.PAYMENT_WEBHOOK_INVALID_SIGNATURE)
    }

    companion object {
        private const val SECRET_PREFIX = "whsec_"
        private const val SIGNATURE_VERSION = "v1"
        private const val HMAC_ALGORITHM = "HmacSHA256"

        /** 재생 공격 허용 오차(초) — Standard Webhooks 권장값 */
        private const val TOLERANCE_SECONDS = 300L

        private val EVENT_TYPES =
            mapOf(
                "Transaction.Paid" to PaymentWebhookNotificationType.PAID,
                "Transaction.Failed" to PaymentWebhookNotificationType.FAILED,
            )

        private val BODY_MAPPER = JsonMapper.builder().build()
    }
}
