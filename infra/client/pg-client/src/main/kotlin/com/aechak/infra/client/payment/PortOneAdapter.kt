package com.aechak.infra.client.payment

import com.aechak.application.payment.port.PaymentCancelStatus
import com.aechak.application.payment.port.PaymentGatewayPort
import com.aechak.application.payment.port.PaymentGatewayStatus
import com.aechak.application.payment.port.PaymentGatewayView
import com.aechak.common.error.BusinessException
import com.aechak.domain.payment.error.PaymentErrorCode
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.client.ClientHttpResponse
import org.springframework.http.converter.HttpMessageConversionException
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import tools.jackson.databind.json.JsonMapper
import java.io.UncheckedIOException
import java.time.OffsetDateTime
import java.time.ZoneId

@Component
class PortOneAdapter(
    @Qualifier(PortOneConfig.PORT_ONE_REST_CLIENT) private val restClient: RestClient,
    private val properties: PortOneProperties,
) : PaymentGatewayPort {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun preRegister(
        paymentId: String,
        amount: Long,
    ) {
        requireApiSecret()
        try {
            restClient
                .post()
                .uri(PRE_REGISTER_PATH, paymentId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(PreRegisterRequest(totalAmount = amount, currency = KRW))
                .retrieve()
                .onStatus({ it.value() == HttpStatus.CONFLICT.value() }) { _, response ->
                    // 409인 경우 -> 이미 결제한 경우
                    val type = logFailure(PRE_REGISTER_PAYMENT_OPERATION, paymentId, response)
                    if (type == ALREADY_PAID_TYPE) throw BusinessException(PaymentErrorCode.PAYMENT_ALREADY_PAID)
                    throw BusinessException(PaymentErrorCode.PAYMENT_GATEWAY_ERROR)
                }.onStatus({ it.isError }) { _, response ->
                    logFailure(PRE_REGISTER_PAYMENT_OPERATION, paymentId, response)
                    throw BusinessException(PaymentErrorCode.PAYMENT_GATEWAY_ERROR)
                }.toBodilessEntity() // 성공 본문은 빈 객체
        } catch (e: HttpMessageConversionException) {
            throw gatewayFailure(PRE_REGISTER_PAYMENT_OPERATION, paymentId, e)
        } catch (e: RestClientException) {
            throw gatewayFailure(PRE_REGISTER_PAYMENT_OPERATION, paymentId, e)
        } catch (e: UncheckedIOException) {
            throw gatewayFailure(PRE_REGISTER_PAYMENT_OPERATION, paymentId, e)
        }
    }

    override fun find(paymentId: String): PaymentGatewayView? {
        requireApiSecret()
        val response =
            try {
                restClient
                    .get()
                    .uri(PAYMENT_PATH, paymentId)
                    .retrieve()
                    .onStatus({ it.value() == HttpStatus.NOT_FOUND.value() }) { _, response ->
                        val type = logFailure(FIND_PAYMENT_OPERATION, paymentId, response, quietType = PAYMENT_NOT_FOUND_TYPE)
                        // 포트원이 낸 404만 없는 건으로 접음. 프록시가 낸 404를 접으면 승인된 결제가 없는 것으로 읽힘
                        if (type == PAYMENT_NOT_FOUND_TYPE) throw GatewayPaymentAbsent()
                        throw BusinessException(PaymentErrorCode.PAYMENT_GATEWAY_ERROR)
                    }.onStatus({ it.isError }) { _, response ->
                        logFailure(FIND_PAYMENT_OPERATION, paymentId, response)
                        throw BusinessException(PaymentErrorCode.PAYMENT_GATEWAY_ERROR)
                    }.body(PaymentResponse::class.java)
            } catch (e: GatewayPaymentAbsent) {
                // 아직 결제를 진행하지 않아서 찾을 수 없는 경우
                return null
            } catch (e: HttpMessageConversionException) {
                throw gatewayFailure(FIND_PAYMENT_OPERATION, paymentId, e)
            } catch (e: RestClientException) {
                throw gatewayFailure(FIND_PAYMENT_OPERATION, paymentId, e)
            } catch (e: UncheckedIOException) {
                throw gatewayFailure(FIND_PAYMENT_OPERATION, paymentId, e)
            }

        if (response == null) throw BusinessException(PaymentErrorCode.PAYMENT_GATEWAY_ERROR) // 200 인데 null인 경우

        return PaymentGatewayView(
            status = response.status,
            totalAmount = response.amount.total,
            paidAmount = response.amount.paid,
            pgTxId = response.pgTxId,
            paidAt = response.paidAt?.atZoneSameInstant(KST)?.toLocalDateTime(),
        )
    }

    override fun cancel(
        paymentId: String,
        reason: String,
    ): PaymentCancelStatus {
        requireApiSecret()
        val response =
            try {
                restClient
                    .post()
                    .uri(CANCEL_PATH, paymentId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(CancelRequest(reason = reason))
                    .retrieve()
                    .onStatus({ it.isError }) { _, response ->
                        logFailure(CANCEL_PAYMENT_OPERATION, paymentId, response)
                        throw BusinessException(PaymentErrorCode.PAYMENT_GATEWAY_ERROR)
                    }.body(CancelResponse::class.java)
            } catch (e: HttpMessageConversionException) {
                throw gatewayFailure(CANCEL_PAYMENT_OPERATION, paymentId, e)
            } catch (e: RestClientException) {
                throw gatewayFailure(CANCEL_PAYMENT_OPERATION, paymentId, e)
            } catch (e: UncheckedIOException) {
                throw gatewayFailure(CANCEL_PAYMENT_OPERATION, paymentId, e)
            }
        if (response == null) throw BusinessException(PaymentErrorCode.PAYMENT_GATEWAY_ERROR)

        return response.cancellation.status
    }

    private fun requireApiSecret() {
        if (properties.apiSecret.isBlank()) {
            log.error("포트원 API 시크릿이 설정되지 않아 호출을 중단함")
            throw BusinessException(PaymentErrorCode.PAYMENT_GATEWAY_ERROR)
        }
    }

    private fun gatewayFailure(
        operation: String,
        paymentId: String,
        e: Exception,
    ): BusinessException {
        log.warn("포트원 {} 실패. paymentId={}", operation, paymentId, e)
        return BusinessException(PaymentErrorCode.PAYMENT_GATEWAY_ERROR, e)
    }

    /**
     * 오류 본문을 읽어 로그로 남기고 type을 돌려줌.
     */
    private fun logFailure(
        operation: String,
        paymentId: String,
        response: ClientHttpResponse,
        quietType: String? = null,
    ): String? {
        val body = runCatching { response.body.readNBytes(LOG_BODY_MAX_LENGTH).decodeToString() }.getOrDefault("")
        val type = runCatching { ERROR_BODY_MAPPER.readTree(body).path("type").stringValue() }.getOrNull()
        val status = response.statusCode.value()
        if (type != null && type == quietType) {
            log.debug("포트원 {} 대상 없음. paymentId={}, status={}", operation, paymentId, status)
        } else {
            log.warn("포트원 {} 실패. paymentId={}, status={}, body={}", operation, paymentId, status, body)
        }
        return type
    }

    companion object {
        private const val PAYMENT_PATH = "/payments/{paymentId}"
        private const val PRE_REGISTER_PATH = "/payments/{paymentId}/pre-register"
        private const val CANCEL_PATH = "/payments/{paymentId}/cancel"

        // 로그 라벨
        private const val FIND_PAYMENT_OPERATION = "GET $PAYMENT_PATH"
        private const val PRE_REGISTER_PAYMENT_OPERATION = "POST $PRE_REGISTER_PATH"
        private const val CANCEL_PAYMENT_OPERATION = "POST $CANCEL_PATH"
        private const val KRW = "KRW"

        /** 포트원 오류 본문 판별값 */
        private const val ALREADY_PAID_TYPE = "ALREADY_PAID"
        private const val PAYMENT_NOT_FOUND_TYPE = "PAYMENT_NOT_FOUND"

        /** 오류 본문에서 type 필드만 뽑는 전용 파서 */
        private val ERROR_BODY_MAPPER = JsonMapper.builder().build()

        /** 오류 본문 읽기 상한. 프록시가 낸 HTML 전문까지 읽지 않게 끊음. */
        private const val LOG_BODY_MAX_LENGTH = 1000

        /** paidAt 오프셋을 KST 로 환산. 서버 TZ와 무관하게 같은 값임. */
        private val KST = ZoneId.of("Asia/Seoul")
    }
}

/** onStatus 핸들러는 값을 못 돌려주므로 404를 이 예외로 올려 find에서 null로 바꿈. */
private class GatewayPaymentAbsent : RuntimeException()

private data class PreRegisterRequest(
    val totalAmount: Long,
    val currency: String,
)

private data class CancelRequest(
    val reason: String,
)

private data class PaymentResponse(
    val status: PaymentGatewayStatus,
    val amount: Amount,
    val pgTxId: String?,
    val paidAt: OffsetDateTime?,
) {
    data class Amount(
        val total: Long,
        val paid: Long,
    )
}

private data class CancelResponse(
    val cancellation: Cancellation,
) {
    data class Cancellation(
        val status: PaymentCancelStatus,
    )
}
