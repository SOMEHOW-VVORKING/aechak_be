package com.aechak.infra.client.payment.reconciliation

import com.aechak.application.payment.port.GatewayPaymentRecord
import com.aechak.application.payment.port.PaymentGatewayStatus
import com.aechak.application.payment.port.PaymentReconciliationSourcePort
import com.aechak.common.error.BusinessException
import com.aechak.domain.payment.error.PaymentErrorCode
import com.aechak.domain.payment.reconciliation.ReconciliationSource
import com.aechak.infra.client.payment.PortOneProperties
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
 * 포트원 V2 결제 다건 조회(`GET /payments`)로 상태 변경 창 안의 결제를 전부 받는다.
 * GET 본문은 표준 밖이라 포트원 안내대로 JSON을 `requestBody` 쿼리에 URL 인코딩해 보낸다.
 * 필터 enum은 PENDING, 응답 판별값은 PAY_PENDING으로 이름이 다르다 — 상태 필터는 쓰지 않고 응답만 번역한다.
 */
class PortOneReconciliationSource(
    private val restClient: RestClient,
    private val portOneProperties: PortOneProperties,
    private val properties: PortOneReconciliationProperties,
) : PaymentReconciliationSourcePort {
    private val log = LoggerFactory.getLogger(javaClass)

    override val source: ReconciliationSource = ReconciliationSource.PORTONE
    override val coversInternalLedger: Boolean = true
    override val supportsAutoConfirmation: Boolean = true

    override fun fetchChangedPayments(
        from: LocalDateTime,
        until: LocalDateTime,
    ): List<GatewayPaymentRecord> {
        requireApiSecret()
        val result = mutableListOf<GatewayPaymentRecord>()
        var pageNumber = 0
        while (pageNumber < MAX_PAGES) {
            val page = fetchPage(from, until, pageNumber)
            result += page.items.map { it.toRecord() }
            if (page.items.size < properties.pageSize) break
            pageNumber++
        }
        if (pageNumber >= MAX_PAGES) {
            // 창 하나에 만 건을 넘기면 페이지 상한에 걸린다 — 조용히 자르지 않고 회차를 실패시켜 사람이 창을 쪼개게 한다
            throw BusinessException(PaymentErrorCode.RECONCILIATION_SOURCE_ERROR, detail = "페이지 상한($MAX_PAGES) 초과")
        }
        log.info("포트원 대사 목록 조회. from={}, until={}, pages={}, items={}", from, until, pageNumber + 1, result.size)
        return result
    }

    override fun findByPaymentKey(paymentKey: String): GatewayPaymentRecord? {
        requireApiSecret()
        return try {
            restClient
                .get()
                .uri(PAYMENT_PATH, paymentKey)
                .retrieve()
                .onStatus({ it.value() == HttpStatus.NOT_FOUND.value() }) { _, response ->
                    // 포트원이 낸 "결제 없음"만 없는 건으로 접는다. 프록시의 404를 접으면 승인된 결제가 없는 것으로 읽힌다
                    if (errorType(response) == PAYMENT_NOT_FOUND_TYPE) throw GatewayPaymentAbsent()
                    throw BusinessException(PaymentErrorCode.RECONCILIATION_SOURCE_ERROR)
                }.onStatus({ it.isError }) { _, response ->
                    logFailure("GET $PAYMENT_PATH", response)
                    throw BusinessException(PaymentErrorCode.RECONCILIATION_SOURCE_ERROR)
                }.body(PaymentItem::class.java)
                ?.toRecord()
                ?: throw BusinessException(PaymentErrorCode.RECONCILIATION_SOURCE_ERROR, detail = "빈 응답")
        } catch (e: GatewayPaymentAbsent) {
            null
        } catch (e: BusinessException) {
            throw e
        } catch (e: Exception) {
            log.warn("포트원 결제 단건 조회 실패. paymentKey={}", paymentKey, e)
            throw BusinessException(PaymentErrorCode.RECONCILIATION_SOURCE_ERROR, e)
        }
    }

    private fun fetchPage(
        from: LocalDateTime,
        until: LocalDateTime,
        pageNumber: Int,
    ): PaymentsPage {
        val filter =
            mutableMapOf<String, Any>(
                "timestampType" to "STATUS_CHANGED_AT",
                "from" to from.format(),
                "until" to until.format(),
                "sortBy" to "STATUS_CHANGED_AT",
                "sortOrder" to "ASC",
            )
        properties.isTest?.let { filter["isTest"] = it }
        val requestBody =
            REQUEST_MAPPER.writeValueAsString(
                mapOf(
                    "page" to mapOf("number" to pageNumber, "size" to properties.pageSize),
                    "filter" to filter,
                ),
            )
        return try {
            restClient
                .get()
                .uri(PAYMENTS_PATH_TEMPLATE, requestBody) // URI 변수는 엄격히 인코딩된다 — JSON의 중괄호가 템플릿으로 읽히지 않게 변수로 넘긴다
                .retrieve()
                .onStatus({ it.isError }) { _, response ->
                    logFailure("GET /payments", response)
                    throw BusinessException(PaymentErrorCode.RECONCILIATION_SOURCE_ERROR)
                }.body(PaymentsPage::class.java)
                ?: throw BusinessException(PaymentErrorCode.RECONCILIATION_SOURCE_ERROR, detail = "빈 응답")
        } catch (e: BusinessException) {
            throw e
        } catch (e: Exception) {
            log.warn("포트원 결제 목록 조회 실패. page={}", pageNumber, e)
            throw BusinessException(PaymentErrorCode.RECONCILIATION_SOURCE_ERROR, e)
        }
    }

    private fun requireApiSecret() {
        if (portOneProperties.apiSecret.isBlank()) {
            log.error("포트원 API 시크릿이 설정되지 않아 대사 조회를 중단함")
            throw BusinessException(PaymentErrorCode.RECONCILIATION_SOURCE_ERROR, detail = "API 시크릿 미설정")
        }
    }

    private fun errorType(response: ClientHttpResponse): String? {
        val body = runCatching { response.body.readNBytes(LOG_BODY_MAX_LENGTH).decodeToString() }.getOrDefault("")
        return runCatching { ERROR_BODY_MAPPER.readTree(body).path("type").stringValue() }.getOrNull()
    }

    private fun logFailure(
        operation: String,
        response: ClientHttpResponse,
    ) {
        val body = runCatching { response.body.readNBytes(LOG_BODY_MAX_LENGTH).decodeToString() }.getOrDefault("")
        log.warn("포트원 {} 실패. status={}, body={}", operation, response.statusCode.value(), body)
    }

    private fun LocalDateTime.format(): String = atZone(KST).format(RFC3339)

    private fun PaymentItem.toRecord(): GatewayPaymentRecord =
        GatewayPaymentRecord(
            paymentKey = id,
            status = status,
            totalAmount = amount.total,
            paidAmount = amount.paid,
            cancelledAmount = amount.cancelled,
            pgTxId = pgTxId,
            statusChangedAt = statusChangedAt?.atZoneSameInstant(KST)?.toLocalDateTime(),
        )

    companion object {
        private const val PAYMENTS_PATH_TEMPLATE = "/payments?requestBody={requestBody}"
        private const val PAYMENT_PATH = "/payments/{paymentId}"
        private const val PAYMENT_NOT_FOUND_TYPE = "PAYMENT_NOT_FOUND"
        private const val LOG_BODY_MAX_LENGTH = 1000

        /** 페이지 100건 × 100 = 창 하나에 1만 건. 넘으면 창을 쪼개야 한다 */
        private const val MAX_PAGES = 100

        private val KST = ZoneId.of("Asia/Seoul")
        private val RFC3339 = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX")
        private val REQUEST_MAPPER = JsonMapper.builder().build()
        private val ERROR_BODY_MAPPER = JsonMapper.builder().build()
    }
}

private data class PaymentsPage(
    val items: List<PaymentItem> = emptyList(),
)

/** 포트원 Payment 유니온의 공통 필드만 읽는다. 상태별 전용 필드는 대사에 필요 없다 */
private data class PaymentItem(
    val id: String,
    val status: PaymentGatewayStatus,
    val amount: Amount,
    val pgTxId: String? = null,
    val statusChangedAt: OffsetDateTime? = null,
) {
    data class Amount(
        val total: Long,
        val paid: Long = 0L,
        val cancelled: Long = 0L,
    )
}
