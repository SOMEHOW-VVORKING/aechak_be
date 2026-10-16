package com.aechak.infra.client.payment

import com.aechak.application.payment.port.PaymentWebhookNotificationType
import com.aechak.application.payment.port.PaymentWebhookRequest
import com.aechak.common.error.BusinessException
import com.aechak.domain.payment.error.PaymentErrorCode
import java.time.Instant
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * 계약. 웹훅 서명 검증 규칙(Standard Webhooks)과 처리 대상 사건 선별을 고정한다.
 * 깨지면 아무나 보낸 통보로 주문이 결제완료가 되거나, 정상 통보를 계속 거절해 확정이 콜백에만 의존하게 된다.
 */
class PortOneWebhookVerifierTest {
    private val verifier = PortOneWebhookVerifier(PortOneProperties(webhookSecret = SECRET))

    @Test
    fun `서명이 맞으면 결제 식별자와 사건 종류로 옮긴다`() {
        val event = verifier.verify(signedRequest(PAID_BODY))

        assertNotNull(event, "유효한 승인 통보는 확정 흐름을 태워야 한다")
        assertEquals("order-1", event.paymentId)
        assertEquals(PaymentWebhookNotificationType.PAID, event.type)
    }

    @Test
    fun `실패 통보도 확정 흐름을 태운다`() {
        val body = PAID_BODY.replace("Transaction.Paid", "Transaction.Failed")

        assertEquals(PaymentWebhookNotificationType.FAILED, verifier.verify(signedRequest(body))?.type)
    }

    @Test
    fun `본문이 한 글자만 달라도 거절한다`() {
        val request = signedRequest(PAID_BODY).copy(rawBody = PAID_BODY.replace("order-1", "order-2"))

        assertRejected { verifier.verify(request) }
    }

    @Test
    fun `서명 헤더가 없으면 거절한다`() {
        assertRejected { verifier.verify(signedRequest(PAID_BODY).copy(signature = null)) }
        assertRejected { verifier.verify(signedRequest(PAID_BODY).copy(id = null)) }
        assertRejected { verifier.verify(signedRequest(PAID_BODY).copy(timestamp = null)) }
    }

    @Test
    fun `허용 시각을 벗어난 통보는 서명이 맞아도 거절한다`() {
        // 가로챈 요청을 그대로 다시 보내는 재생 공격 — 서명은 그대로라 시각만이 방어선이다
        val staleAt = Instant.now().epochSecond - 3_600
        val request = signedRequest(PAID_BODY, timestamp = staleAt.toString())

        assertRejected { verifier.verify(request) }
    }

    @Test
    fun `서명이 여러 개면 하나만 맞아도 통과한다`() {
        // 시크릿 교체 중에는 옛 키와 새 키의 서명이 함께 온다
        val valid = signedRequest(PAID_BODY)
        val request = valid.copy(signature = "v1,ZmFrZS1zaWduYXR1cmU= ${valid.signature}")

        assertNotNull(verifier.verify(request))
    }

    @Test
    fun `확정과 무관한 사건은 넘긴다`() {
        val body = PAID_BODY.replace("Transaction.Paid", "Transaction.VirtualAccountIssued")

        assertNull(verifier.verify(signedRequest(body)), "가상계좌 발급 통보로 확정을 시도하면 안 된다")
    }

    @Test
    fun `결제 식별자가 없는 통보는 넘긴다`() {
        val body = """{"type":"Transaction.Paid","data":{"transactionId":"tx-1"}}"""

        assertNull(verifier.verify(signedRequest(body)))
    }

    @Test
    fun `시크릿이 없으면 검증 수단이 없으므로 게이트웨이 오류로 끊는다`() {
        // 401로 거절하면 설정 누락이 위조 시도로 보인다 — 재전송으로 회복 가능한 사건이라 502로 구분한다
        val unconfigured = PortOneWebhookVerifier(PortOneProperties())

        val e = assertFailsWith<BusinessException> { unconfigured.verify(signedRequest(PAID_BODY)) }
        assertEquals(PaymentErrorCode.PAYMENT_GATEWAY_ERROR, e.errorCode)
    }

    private fun assertRejected(block: () -> Unit) {
        val e = assertFailsWith<BusinessException>(block = block)
        assertEquals(PaymentErrorCode.PAYMENT_WEBHOOK_INVALID_SIGNATURE, e.errorCode)
    }

    private fun signedRequest(
        body: String,
        id: String = "whid-1",
        timestamp: String = Instant.now().epochSecond.toString(),
    ): PaymentWebhookRequest =
        PaymentWebhookRequest(
            rawBody = body,
            id = id,
            timestamp = timestamp,
            signature = sign(id, timestamp, body),
        )

    private fun sign(
        id: String,
        timestamp: String,
        body: String,
    ): String {
        val key = Base64.getDecoder().decode(SECRET.removePrefix("whsec_"))
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return "v1,${Base64.getEncoder().encodeToString(mac.doFinal("$id.$timestamp.$body".toByteArray()))}"
    }

    companion object {
        private const val SECRET = "whsec_dGVzdC13ZWJob29rLXNlY3JldC1rZXktMTIzNDU2"

        private const val PAID_BODY =
            """{"type":"Transaction.Paid","timestamp":"2026-08-28T00:00:00Z",""" +
                """"data":{"paymentId":"order-1","transactionId":"tx-1","storeId":"store-test-1"}}"""
    }
}
