package com.aechak.infra.client.payment.reconciliation

import com.aechak.application.payment.port.GatewayPaymentRecord
import com.aechak.application.payment.port.PaymentGatewayStatus
import com.aechak.application.payment.port.PaymentReconciliationSourcePort
import com.aechak.common.error.BusinessException
import com.aechak.domain.payment.error.PaymentErrorCode
import com.aechak.domain.payment.reconciliation.ReconciliationSource
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.client.ClientHttpResponse
import org.springframework.web.client.RestClient
import tools.jackson.databind.json.JsonMapper
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 토스페이먼츠 코어 API 기반 대사 소스.
 * 거래 조회(`GET /v1/transactions`, transactionKey 커서)로 창 안에 거래가 있던 결제를 모으고,
 * 결제마다 `GET /v1/payments/{paymentKey}`로 최종 상태·금액을 받는다. 우리 결제 식별자는 토스의 orderId다.
 * 단건 재확인은 `GET /v1/payments/orders/{orderId}` — paymentKey는 토스가 발급한 값이라 우리 식별자로 쓸 수 없다.
 */
class TossPaymentsReconciliationSource(
    private val restClient: RestClient,
    private val properties: TossPaymentsProperties,
) : PaymentReconciliationSourcePort {
    private val log = LoggerFactory.getLogger(javaClass)

    override val source: ReconciliationSource = ReconciliationSource.TOSS
    override val coversInternalLedger: Boolean = false
    override val supportsAutoConfirmation: Boolean = false

    override fun fetchChangedPayments(
        from: LocalDateTime,
        until: LocalDateTime,
    ): List<GatewayPaymentRecord> {
        requireSecretKey()
        val paymentKeys = linkedSetOf<String>()
        var startingAfter: String? = null
        var pages = 0
        while (pages < MAX_PAGES) {
            val transactions = fetchTransactions(from, until, startingAfter)
            transactions.forEach { paymentKeys += it.paymentKey }
            if (transactions.size < properties.pageSize) break
            // 커서는 중복 제거 전 원본 응답의 마지막 행에서 진행한다
            startingAfter = transactions.last().transactionKey
            pages++
        }
        if (pages >= MAX_PAGES) throw BusinessException(PaymentErrorCode.RECONCILIATION_SOURCE_ERROR, detail = "페이지 상한($MAX_PAGES) 초과")
        val records = paymentKeys.map { key -> fetchPayment(PAYMENT_PATH, key).toRecord() }
        log.info("토스 대사 목록 조회. from={}, until={}, transactionsPages={}, payments={}", from, until, pages + 1, records.size)
        return records
    }

    override fun findByPaymentKey(paymentKey: String): GatewayPaymentRecord? {
        requireSecretKey()
        return try {
            fetchPayment(PAYMENT_BY_ORDER_PATH, paymentKey).toRecord()
        } catch (e: GatewayPaymentAbsent) {
            null
        }
    }

    private fun fetchTransactions(
        from: LocalDateTime,
        until: LocalDateTime,
        startingAfter: String?,
    ): List<Transaction> =
        try {
            restClient
                .get()
                .uri { builder ->
                    builder
                        .path(TRANSACTIONS_PATH)
                        .queryParam("startDate", from.format(TOSS_DATE_TIME))
                        .queryParam("endDate", until.format(TOSS_DATE_TIME))
                        .queryParam("limit", properties.pageSize)
                        .apply { startingAfter?.let { queryParam("startingAfter", it) } }
                        .build()
                }.retrieve()
                .onStatus({ it.isError }) { _, response ->
                    logFailure("GET $TRANSACTIONS_PATH", response)
                    throw BusinessException(PaymentErrorCode.RECONCILIATION_SOURCE_ERROR)
                }.body(Array<Transaction>::class.java)
                ?.toList()
                ?: emptyList()
        } catch (e: BusinessException) {
            throw e
        } catch (e: Exception) {
            log.warn("토스 거래 조회 실패. startingAfter={}", startingAfter, e)
            throw BusinessException(PaymentErrorCode.RECONCILIATION_SOURCE_ERROR, e)
        }

    private fun fetchPayment(
        pathTemplate: String,
        key: String,
    ): TossPayment =
        try {
            restClient
                .get()
                .uri(pathTemplate, key)
                .retrieve()
                .onStatus({ it.value() == HttpStatus.NOT_FOUND.value() }) { _, response ->
                    if (errorCode(response) == NOT_FOUND_PAYMENT_CODE) throw GatewayPaymentAbsent()
                    throw BusinessException(PaymentErrorCode.RECONCILIATION_SOURCE_ERROR)
                }.onStatus({ it.isError }) { _, response ->
                    logFailure("GET $pathTemplate", response)
                    throw BusinessException(PaymentErrorCode.RECONCILIATION_SOURCE_ERROR)
                }.body(TossPayment::class.java)
                ?: throw BusinessException(PaymentErrorCode.RECONCILIATION_SOURCE_ERROR, detail = "빈 응답")
        } catch (e: GatewayPaymentAbsent) {
            throw e
        } catch (e: BusinessException) {
            throw e
        } catch (e: Exception) {
            log.warn("토스 결제 조회 실패. key={}", key, e)
            throw BusinessException(PaymentErrorCode.RECONCILIATION_SOURCE_ERROR, e)
        }

    private fun requireSecretKey() {
        if (properties.secretKey.isBlank()) {
            log.error("토스 시크릿 키가 설정되지 않아 대사 조회를 중단함")
            throw BusinessException(PaymentErrorCode.RECONCILIATION_SOURCE_ERROR, detail = "시크릿 키 미설정")
        }
    }

    private fun errorCode(response: ClientHttpResponse): String? {
        val body = runCatching { response.body.readNBytes(LOG_BODY_MAX_LENGTH).decodeToString() }.getOrDefault("")
        return runCatching { ERROR_BODY_MAPPER.readTree(body).path("code").stringValue() }.getOrNull()
    }

    private fun logFailure(
        operation: String,
        response: ClientHttpResponse,
    ) {
        val body = runCatching { response.body.readNBytes(LOG_BODY_MAX_LENGTH).decodeToString() }.getOrDefault("")
        log.warn("토스 {} 실패. status={}, body={}", operation, response.statusCode.value(), body)
    }

    /**
     * 토스 상태를 우리 어휘로. 금액은 총액이 승인액이고 잔액이 취소가능액이라 취소액 = 총액 − 잔액.
     * 미성사 상태(READY·IN_PROGRESS·ABORTED·EXPIRED)는 돈이 안 들어온 것이라 승인액 0.
     */
    private fun TossPayment.toRecord(): GatewayPaymentRecord {
        val status =
            when (this.status) {
                "DONE" -> PaymentGatewayStatus.PAID
                "CANCELED" -> PaymentGatewayStatus.CANCELLED
                "PARTIAL_CANCELED" -> PaymentGatewayStatus.PARTIAL_CANCELLED
                "WAITING_FOR_DEPOSIT" -> PaymentGatewayStatus.VIRTUAL_ACCOUNT_ISSUED
                "ABORTED", "EXPIRED" -> PaymentGatewayStatus.FAILED
                else -> PaymentGatewayStatus.READY // READY · IN_PROGRESS
            }
        val settled =
            status == PaymentGatewayStatus.PAID ||
                status == PaymentGatewayStatus.CANCELLED ||
                status == PaymentGatewayStatus.PARTIAL_CANCELLED
        val paid = if (settled) totalAmount else 0L
        val cancelled = if (settled) (totalAmount - (balanceAmount ?: totalAmount)).coerceAtLeast(0L) else 0L
        val lastCancelledAt = cancels.orEmpty().mapNotNull { it.canceledAt }.maxOrNull()
        return GatewayPaymentRecord(
            paymentKey = orderId,
            status = status,
            totalAmount = totalAmount,
            paidAmount = paid,
            cancelledAmount = cancelled,
            pgTxId = paymentKey,
            statusChangedAt = (lastCancelledAt ?: approvedAt)?.atZoneSameInstant(KST)?.toLocalDateTime(),
        )
    }

    companion object {
        private const val TRANSACTIONS_PATH = "/v1/transactions"
        private const val PAYMENT_PATH = "/v1/payments/{paymentKey}"
        private const val PAYMENT_BY_ORDER_PATH = "/v1/payments/orders/{orderId}"
        private const val NOT_FOUND_PAYMENT_CODE = "NOT_FOUND_PAYMENT"
        private const val LOG_BODY_MAX_LENGTH = 1000
        private const val MAX_PAGES = 100

        private val KST = ZoneId.of("Asia/Seoul")
        private val TOSS_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
        private val ERROR_BODY_MAPPER = JsonMapper.builder().build()
    }
}

private data class Transaction(
    val transactionKey: String,
    val paymentKey: String,
    val orderId: String,
)

private data class TossPayment(
    val paymentKey: String,
    val orderId: String,
    val status: String,
    val totalAmount: Long,
    val balanceAmount: Long? = null,
    val approvedAt: OffsetDateTime? = null,
    val cancels: List<Cancel>? = null,
) {
    data class Cancel(
        val cancelAmount: Long? = null,
        val canceledAt: OffsetDateTime? = null,
    )
}
