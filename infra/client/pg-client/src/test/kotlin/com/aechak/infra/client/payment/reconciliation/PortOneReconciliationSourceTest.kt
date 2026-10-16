package com.aechak.infra.client.payment.reconciliation

import com.aechak.application.payment.port.PaymentGatewayStatus
import com.aechak.common.error.BusinessException
import com.aechak.infra.client.payment.PortOneProperties
import org.hamcrest.Matchers.startsWith
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import java.net.URLDecoder
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 계약. 포트원 다건 조회의 요청 규격(requestBody 쿼리·창·정렬)과 페이지 순회, 응답 번역을 고정한다.
 * 깨지면 창 밖의 결제를 대조하거나, 두 번째 페이지를 놓치거나, 프록시 404를 "PG에 없음"으로 읽는다.
 */
class PortOneReconciliationSourceTest {
    private val builder = RestClient.builder().baseUrl(BASE_URL)
    private val server = MockRestServiceServer.bindTo(builder).build()
    private val restClient = builder.build()
    private val source =
        PortOneReconciliationSource(
            restClient,
            PortOneProperties(apiSecret = "test-secret"),
            PortOneReconciliationProperties(isTest = true, pageSize = 2),
        )
    private val from = LocalDateTime.of(2026, 10, 14, 0, 0)
    private val until = LocalDateTime.of(2026, 10, 16, 0, 0)

    @Test
    fun `창과 정렬과 테스트 여부를 requestBody 쿼리에 JSON으로 실어 보낸다`() {
        server
            .expect(requestTo(startsWith("$BASE_URL/payments?requestBody=")))
            .andExpect(method(HttpMethod.GET))
            .andExpect { request ->
                val body = URLDecoder.decode(request.uri.rawQuery.substringAfter("requestBody="), Charsets.UTF_8)
                assertTrue(body.contains("\"timestampType\":\"STATUS_CHANGED_AT\""), body)
                assertTrue(body.contains("\"from\":\"2026-10-14T00:00:00+09:00\""), "창 시작은 KST 오프셋을 단 RFC 3339여야 한다: $body")
                assertTrue(body.contains("\"until\":\"2026-10-16T00:00:00+09:00\""), body)
                assertTrue(body.contains("\"sortBy\":\"STATUS_CHANGED_AT\"") && body.contains("\"sortOrder\":\"ASC\""), body)
                assertTrue(body.contains("\"isTest\":true"), body)
                assertTrue(body.contains("\"page\":{\"number\":0,\"size\":2}"), body)
            }.andRespond(withSuccess(page(listOf(paidItem("pay-1"))), MediaType.APPLICATION_JSON))

        val records = source.fetchChangedPayments(from, until)

        assertEquals(1, records.size)
        server.verify()
    }

    @Test
    fun `페이지가 가득 차면 다음 번호를 묻고 모자라면 멈춘다`() {
        server
            .expect(requestTo(startsWith("$BASE_URL/payments?requestBody=")))
            .andExpect { assertTrue(decodedBody(it.uri.rawQuery).contains("\"number\":0")) }
            .andRespond(withSuccess(page(listOf(paidItem("pay-1"), cancelledItem("pay-2"))), MediaType.APPLICATION_JSON))
        server
            .expect(requestTo(startsWith("$BASE_URL/payments?requestBody=")))
            .andExpect { assertTrue(decodedBody(it.uri.rawQuery).contains("\"number\":1")) }
            .andRespond(withSuccess(page(listOf(failedItem("pay-3"))), MediaType.APPLICATION_JSON))

        val records = source.fetchChangedPayments(from, until)

        assertEquals(listOf("pay-1", "pay-2", "pay-3"), records.map { it.paymentKey })
        val cancelled = records[1]
        assertEquals(PaymentGatewayStatus.CANCELLED, cancelled.status)
        assertEquals(10_000L, cancelled.paidAmount)
        assertEquals(10_000L, cancelled.cancelledAmount, "취소액은 amount.cancelled에서 읽는다")
        val failed = records[2]
        assertEquals(PaymentGatewayStatus.FAILED, failed.status)
        assertEquals(0L, failed.paidAmount, "실패 건은 amount.paid가 없어 0으로 읽는다")
        assertEquals(LocalDateTime.of(2026, 10, 15, 19, 15, 30), records[0].statusChangedAt, "UTC 표기를 KST 벽시계로 환산한다")
        server.verify()
    }

    @Test
    fun `목록 조회 5xx는 대사 소스 오류 60101이다`() {
        server
            .expect(requestTo(startsWith("$BASE_URL/payments?requestBody=")))
            .andRespond(withStatus(HttpStatus.BAD_GATEWAY))

        val e = assertFailsWith<BusinessException> { source.fetchChangedPayments(from, until) }

        assertEquals(60101, e.errorCode.code)
    }

    @Test
    fun `단건 404 PaymentNotFound만 PG에 없음이고 다른 404는 오류다`() {
        server
            .expect(requestTo("$BASE_URL/payments/pay-absent"))
            .andRespond(withStatus(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_JSON).body("""{"type":"PAYMENT_NOT_FOUND"}"""))
        server
            .expect(requestTo("$BASE_URL/payments/pay-proxy"))
            .andRespond(withStatus(HttpStatus.NOT_FOUND).contentType(MediaType.TEXT_HTML).body("<html>404</html>"))

        assertNull(source.findByPaymentKey("pay-absent"))
        val e = assertFailsWith<BusinessException> { source.findByPaymentKey("pay-proxy") }
        assertEquals(60101, e.errorCode.code, "프록시 404를 없음으로 접으면 승인된 결제가 '우리만 승인'으로 기록된다")
        server.verify()
    }

    @Test
    fun `단건 200은 같은 규칙으로 번역된다`() {
        server
            .expect(requestTo("$BASE_URL/payments/pay-1"))
            .andRespond(withSuccess(paidItem("pay-1"), MediaType.APPLICATION_JSON))

        val record = source.findByPaymentKey("pay-1")

        assertNotNull(record)
        assertEquals(PaymentGatewayStatus.PAID, record.status)
        assertEquals("pgtx-1", record.pgTxId)
        server.verify()
    }

    @Test
    fun `시크릿이 비어 있으면 호출 전에 끊는다`() {
        val blank = PortOneReconciliationSource(restClient, PortOneProperties(apiSecret = ""), PortOneReconciliationProperties())

        val e = assertFailsWith<BusinessException> { blank.fetchChangedPayments(from, until) }

        assertEquals(60101, e.errorCode.code)
        server.verify() // 기대한 요청이 없으므로 아무 요청도 나가지 않았어야 한다
    }

    private fun decodedBody(rawQuery: String) = URLDecoder.decode(rawQuery.substringAfter("requestBody="), Charsets.UTF_8)

    private fun page(items: List<String>) =
        """{"items":[${items.joinToString(",")}],"page":{"number":0,"size":2,"totalCount":${items.size}}}"""

    private fun paidItem(id: String) =
        """{"status":"PAID","id":"$id","transactionId":"tx-$id","pgTxId":"pgtx-1","amount":{"total":10000,"paid":10000,"cancelled":0},
           "statusChangedAt":"2026-10-15T10:15:30Z","channel":{"type":"TEST"}}"""

    private fun cancelledItem(id: String) =
        """{"status":"CANCELLED","id":"$id","transactionId":"tx-$id","amount":{"total":10000,"paid":10000,"cancelled":10000},
           "statusChangedAt":"2026-10-15T11:00:00Z"}"""

    private fun failedItem(id: String) =
        """{"status":"FAILED","id":"$id","transactionId":"tx-$id","amount":{"total":10000},
           "failure":{"reason":"카드 한도 초과","pgCode":"LIMIT"},"statusChangedAt":"2026-10-15T12:00:00Z"}"""

    companion object {
        private const val BASE_URL = "https://portone.test"
    }
}
