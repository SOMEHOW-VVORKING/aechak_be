package com.aechak.infra.client.payment.reconciliation

import com.aechak.application.payment.port.PaymentGatewayStatus
import com.aechak.common.error.BusinessException
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.ExpectedCount
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestToUriTemplate
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * 계약. 토스 거래 조회의 커서 순회와 결제 조회의 상태·금액 번역, 주문번호 기반 단건 조회를 고정한다.
 * 깨지면 커서가 중복 제거 뒤 키로 진행해 거래를 건너뛰거나, 부분취소 건의 승인액을 잔액으로 읽는다.
 */
class TossPaymentsReconciliationSourceTest {
    private val builder = RestClient.builder().baseUrl(BASE_URL)
    private val server = MockRestServiceServer.bindTo(builder).build()
    private val restClient = builder.build()
    private val source =
        TossPaymentsReconciliationSource(restClient, TossPaymentsProperties(enabled = true, secretKey = "test_sk_x", pageSize = 2))
    private val from = LocalDateTime.of(2026, 10, 14, 0, 0)
    private val until = LocalDateTime.of(2026, 10, 16, 0, 0)

    @Test
    fun `거래를 커서로 끝까지 모은 뒤 결제별로 한 번씩 조회해 orderId를 우리 식별자로 삼는다`() {
        server
            .expect(
                requestToUriTemplate(
                    "$BASE_URL/v1/transactions?startDate={s}&endDate={e}&limit={l}",
                    "2026-10-14T00:00:00",
                    "2026-10-16T00:00:00",
                    "2",
                ),
            ).andRespond(withSuccess(transactions(tx("t1", "pk-1", "order-1"), tx("t2", "pk-1", "order-1")), MediaType.APPLICATION_JSON))
        server
            .expect(
                requestToUriTemplate(
                    "$BASE_URL/v1/transactions?startDate={s}&endDate={e}&limit={l}&startingAfter={c}",
                    "2026-10-14T00:00:00",
                    "2026-10-16T00:00:00",
                    "2",
                    "t2",
                ),
            ).andExpect(queryParam("startingAfter", "t2"))
            .andRespond(withSuccess(transactions(tx("t3", "pk-2", "order-2")), MediaType.APPLICATION_JSON))
        server
            .expect(ExpectedCount.once(), requestTo("$BASE_URL/v1/payments/pk-1"))
            .andRespond(withSuccess(payment("pk-1", "order-1", "DONE", total = 10_000L, balance = 10_000L), MediaType.APPLICATION_JSON))
        server
            .expect(ExpectedCount.once(), requestTo("$BASE_URL/v1/payments/pk-2"))
            .andRespond(
                withSuccess(payment("pk-2", "order-2", "PARTIAL_CANCELED", total = 10_000L, balance = 7_000L), MediaType.APPLICATION_JSON),
            )

        val records = source.fetchChangedPayments(from, until)

        assertEquals(listOf("order-1", "order-2"), records.map { it.paymentKey }, "같은 결제의 거래가 둘이어도 결제는 한 번만 조회한다")
        val partial = records[1]
        assertEquals(PaymentGatewayStatus.PARTIAL_CANCELLED, partial.status)
        assertEquals(10_000L, partial.paidAmount, "승인액은 총액이지 잔액이 아니다")
        assertEquals(3_000L, partial.cancelledAmount, "취소액 = 총액 − 잔액")
        assertEquals("pk-2", partial.pgTxId)
        server.verify()
    }

    @Test
    fun `단건 재확인은 주문번호 경로를 쓰고 NOT_FOUND_PAYMENT만 없음으로 접는다`() {
        server
            .expect(requestTo("$BASE_URL/v1/payments/orders/order-9"))
            .andRespond(
                withStatus(
                    HttpStatus.NOT_FOUND,
                ).contentType(MediaType.APPLICATION_JSON).body("""{"code":"NOT_FOUND_PAYMENT","message":"없음"}"""),
            )
        server
            .expect(requestTo("$BASE_URL/v1/payments/orders/order-8"))
            .andRespond(
                withStatus(
                    HttpStatus.NOT_FOUND,
                ).contentType(MediaType.APPLICATION_JSON).body("""{"code":"UNAUTHORIZED_KEY","message":"키"}"""),
            )

        assertNull(source.findByPaymentKey("order-9"))
        val e = assertFailsWith<BusinessException> { source.findByPaymentKey("order-8") }
        assertEquals(60101, e.errorCode.code)
        server.verify()
    }

    @Test
    fun `미성사 상태는 승인액 0으로 읽는다`() {
        server
            .expect(requestTo("$BASE_URL/v1/payments/orders/order-3"))
            .andRespond(withSuccess(payment("pk-3", "order-3", "ABORTED", total = 10_000L, balance = null), MediaType.APPLICATION_JSON))

        val record = source.findByPaymentKey("order-3")

        assertNotNull(record)
        assertEquals(PaymentGatewayStatus.FAILED, record.status)
        assertEquals(0L, record.paidAmount)
        assertEquals(0L, record.cancelledAmount)
        server.verify()
    }

    private fun transactions(vararg rows: String) = "[${rows.joinToString(",")}]"

    private fun tx(
        key: String,
        paymentKey: String,
        orderId: String,
    ) = """{"transactionKey":"$key","paymentKey":"$paymentKey","orderId":"$orderId","status":"DONE","amount":10000}"""

    private fun payment(
        paymentKey: String,
        orderId: String,
        status: String,
        total: Long,
        balance: Long?,
    ) = """{"paymentKey":"$paymentKey","orderId":"$orderId","status":"$status","totalAmount":$total,
           ${balance?.let { "\"balanceAmount\":$it," } ?: ""}"approvedAt":"2026-10-15T19:15:30+09:00","cancels":null}"""

    companion object {
        private const val BASE_URL = "https://toss.test"
    }
}
