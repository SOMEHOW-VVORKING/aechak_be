package com.aechak.infra.client.payment

import com.aechak.application.payment.port.PaymentCancelStatus
import com.aechak.application.payment.port.PaymentGatewayStatus
import com.aechak.common.error.BusinessException
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withException
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import java.net.SocketTimeoutException
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * 계약. 포트원 V2의 응답과 실패를 PaymentGatewayPort 어휘로 옮기는 규칙을 고정한다.
 * 깨지면 결제 상태를 잘못 읽거나, 외부 실패가 502가 아닌 500으로 새거나, 시크릿 없이 실서버로 요청이 나간다.
 */
class PortOneAdapterTest {
    private val builder = RestClient.builder().baseUrl(BASE_URL)
    private val server = MockRestServiceServer.bindTo(builder).build()
    private val restClient = builder.build() // bindTo로 목 requestFactory를 꽂은 뒤에 만들어야 목이 먹음
    private val adapter = PortOneAdapter(restClient, PortOneProperties(apiSecret = "test-secret"))

    @Test
    fun `조회 200 PAID는 상태와 두 금액과 pgTxId와 결제시각을 뷰로 옮긴다`() {
        server
            .expect(requestTo("$BASE_URL/payments/pay-1"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess(PAID_PAYMENT_JSON, MediaType.APPLICATION_JSON))

        val view = adapter.find("pay-1")

        assertNotNull(view, "200 응답은 뷰로 번역돼야 한다")
        assertEquals(PaymentGatewayStatus.PAID, view.status, "status가 그대로 옮겨져야 한다")
        assertEquals(10_000L, view.totalAmount, "amount.total이 totalAmount로 옮겨져야 한다")
        assertEquals(9_000L, view.paidAmount, "amount.paid가 paidAmount로 옮겨져야 한다")
        assertEquals("pgtx-8842", view.pgTxId, "포트원 채번 transactionId가 아니라 PG사 pgTxId를 실어야 한다")
        assertEquals(LocalDateTime.of(2026, 8, 20, 19, 15, 30), view.paidAt, "UTC 표기 paidAt이 KST 벽시계로 환산돼야 한다")
        server.verify()
    }

    @Test
    fun `조회 200 PAY_PENDING은 PAID가 아닌 상태 그대로 옮겨진다`() {
        server
            .expect(requestTo("$BASE_URL/payments/pay-8"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess(PAY_PENDING_PAYMENT_JSON, MediaType.APPLICATION_JSON))

        val view = adapter.find("pay-8")

        assertNotNull(view, "PAID가 아니어도 뷰로 번역돼야 한다")
        assertEquals(PaymentGatewayStatus.PAY_PENDING, view.status, "PAID가 아닌 상태도 그대로 옮겨져야 한다")
        assertEquals(20_000L, view.totalAmount, "amount.total이 totalAmount로 옮겨져야 한다")
        assertEquals(20_000L, view.paidAmount, "amount.paid가 paidAmount로 옮겨져야 한다")
        assertNull(view.paidAt, "결제 전 상태는 paidAt이 없으므로 null이어야 한다")
        server.verify()
    }

    @Test
    fun `조회 404 PaymentNotFoundError는 예외가 아니라 null이다`() {
        server
            .expect(requestTo("$BASE_URL/payments/pay-absent"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(
                withStatus(HttpStatus.NOT_FOUND)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(PAYMENT_NOT_FOUND_JSON),
            )

        assertNull(adapter.find("pay-absent"), "포트원에 없는 건은 예외가 아니라 null이어야 한다")
        server.verify()
    }

    @Test
    fun `조회 404가 포트원 형식이 아니면 null이 아니라 60005다`() {
        server
            .expect(requestTo("$BASE_URL/payments/pay-proxy-404"))
            .andRespond(
                withStatus(HttpStatus.NOT_FOUND)
                    .contentType(MediaType.TEXT_HTML)
                    .body(PROXY_NOT_FOUND_HTML),
            )
        server
            .expect(requestTo("$BASE_URL/payments/pay-foreign-404"))
            .andRespond(
                withStatus(HttpStatus.NOT_FOUND)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(FOREIGN_NOT_FOUND_JSON),
            )

        val noType = assertFailsWith<BusinessException> { adapter.find("pay-proxy-404") }
        val otherType = assertFailsWith<BusinessException> { adapter.find("pay-foreign-404") }

        assertEquals(60005, noType.errorCode.code, "type이 없는 404를 없는 건으로 접으면 승인된 결제가 취소된다")
        assertEquals(60005, otherType.errorCode.code, "PaymentNotFoundError가 아닌 404도 없는 건이 아니어야 한다")
        server.verify()
    }

    @Test
    fun `사전등록 200 빈 객체는 예외 없이 통과한다`() {
        server
            .expect(requestTo("$BASE_URL/payments/pay-2/pre-register"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(jsonPath("$.totalAmount").value(10_000))
            .andExpect(jsonPath("$.currency").value("KRW"))
            .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON))

        adapter.preRegister("pay-2", 10_000L)

        server.verify()
    }

    @Test
    fun `사전등록 409 AlreadyPaidError는 60006으로 번역된다`() {
        server
            .expect(requestTo("$BASE_URL/payments/pay-3/pre-register"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(
                withStatus(HttpStatus.CONFLICT)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(ALREADY_PAID_JSON),
            )

        val e = assertFailsWith<BusinessException> { adapter.preRegister("pay-3", 10_000L) }

        assertEquals(60006, e.errorCode.code, "이미 결제된 건은 60006이어야 한다")
        assertFalse(e.message.orEmpty().contains("ALREADY_PAID"), "포트원 오류 식별자가 예외 message로 새면 안 된다")
        server.verify()
    }

    @Test
    fun `사전등록 409가 AlreadyPaidError가 아니면 60005로 번역된다`() {
        server
            .expect(requestTo("$BASE_URL/payments/pay-proxy-409/pre-register"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(
                withStatus(HttpStatus.CONFLICT)
                    .contentType(MediaType.TEXT_HTML)
                    .body(PROXY_CONFLICT_HTML),
            )

        val e = assertFailsWith<BusinessException> { adapter.preRegister("pay-proxy-409", 10_000L) }

        assertEquals(60005, e.errorCode.code, "포트원이 낸 409가 아니면 이미 결제된 건이 아니라 게이트웨이 실패다")
        server.verify()
    }

    @Test
    fun `취소 200 cancellation status SUCCEEDED는 SUCCEEDED로 번역된다`() {
        server
            .expect(requestTo("$BASE_URL/payments/pay-4/cancel"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(jsonPath("$.reason").value("고객 변심"))
            .andRespond(withSuccess(CANCEL_SUCCEEDED_JSON, MediaType.APPLICATION_JSON))

        val status = adapter.cancel("pay-4", "고객 변심")

        assertEquals(PaymentCancelStatus.SUCCEEDED, status, "cancellation.status가 그대로 번역돼야 한다")
        server.verify()
    }

    @Test
    fun `취소 502 PgProviderError는 60005로 번역된다`() {
        server
            .expect(requestTo("$BASE_URL/payments/pay-5/cancel"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(
                withStatus(HttpStatus.BAD_GATEWAY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(PG_PROVIDER_ERROR_JSON),
            )

        val e = assertFailsWith<BusinessException> { adapter.cancel("pay-5", "관리자 취소") }

        assertEquals(60005, e.errorCode.code, "PG사 오류는 60005여야 한다")
        server.verify()
    }

    @Test
    fun `apiSecret이 비면 요청을 하나도 내보내지 않고 60005로 끊는다`() {
        val guarded = PortOneAdapter(restClient, PortOneProperties(apiSecret = ""))

        val e = assertFailsWith<BusinessException> { guarded.preRegister("pay-6", 10_000L) }

        assertEquals(60005, e.errorCode.code, "시크릿 미설정은 60005여야 한다")
        server.verify() // 기대 요청을 하나도 안 걸었으므로, 한 건이라도 나갔으면 여기서 깨짐
    }

    @Test
    fun `조회 중 SocketTimeoutException은 60005로 번역된다`() {
        server
            .expect(requestTo("$BASE_URL/payments/pay-7"))
            .andRespond(withException(SocketTimeoutException("read timed out")))

        val e = assertFailsWith<BusinessException> { adapter.find("pay-7") }

        assertEquals(60005, e.errorCode.code, "타임아웃과 커넥션 실패는 60005여야 한다")
        server.verify()
    }

    @Test
    fun `조회 200 본문이 역직렬화되지 않으면 60005로 번역된다`() {
        server
            .expect(requestTo("$BASE_URL/payments/pay-9"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess(BROKEN_PAYMENT_JSON, MediaType.APPLICATION_JSON))

        val e = assertFailsWith<BusinessException> { adapter.find("pay-9") }

        assertEquals(60005, e.errorCode.code, "역직렬화 실패는 500이 아니라 60005여야 한다")
        server.verify()
    }

    companion object {
        private const val BASE_URL = "https://api.portone.io"

        /** 우리가 안 읽는 필드를 일부러 섞음. 관용 리더가 아니면 실응답이 통째로 파싱에 실패함. */
        private val PAID_PAYMENT_JSON =
            """
            {
              "status": "PAID",
              "id": "pay-1",
              "transactionId": "portone-tx-1",
              "merchantId": "merchant-1",
              "storeId": "store-1",
              "orderName": "애착 사료 외 1건",
              "currency": "KRW",
              "amount": {
                "total": 10000,
                "taxFree": 0,
                "vat": 819,
                "supply": 9181,
                "discount": 1000,
                "paid": 9000,
                "cancelled": 0,
                "cancelledTaxFree": 0
              },
              "pgTxId": "pgtx-8842",
              "paidAt": "2026-08-20T10:15:30Z"
            }
            """.trimIndent()

        /** paidAt과 pgTxId를 뺌. 결제 전 상태에서는 둘 다 안 오는데 그때도 뷰가 만들어져야 함. */
        private val PAY_PENDING_PAYMENT_JSON =
            """
            {
              "status": "PAY_PENDING",
              "id": "pay-8",
              "transactionId": "portone-tx-8",
              "merchantId": "merchant-1",
              "storeId": "store-1",
              "orderName": "애착 사료",
              "currency": "KRW",
              "amount": {
                "total": 20000,
                "taxFree": 0,
                "vat": 1818,
                "supply": 18182,
                "discount": 0,
                "paid": 20000,
                "cancelled": 0,
                "cancelledTaxFree": 0
              }
            }
            """.trimIndent()

        /** 응답 스키마가 바뀌어 amount.paid가 사라진 모양. 금액을 0으로 읽고 넘어가면 대사가 조용히 어긋남. */
        private val BROKEN_PAYMENT_JSON =
            """
            {"status": "PAID", "amount": {"total": 10000}, "pgTxId": "pgtx-8843"}
            """.trimIndent()

        private val PAYMENT_NOT_FOUND_JSON =
            """
            {"type": "PAYMENT_NOT_FOUND", "message": "결제 건이 존재하지 않습니다."}
            """.trimIndent()

        /** 프록시나 WAF가 포트원 앞에서 가로챈 응답. 포트원 오류 봉투가 아니라 type이 아예 없음. */
        private const val PROXY_NOT_FOUND_HTML = "<html><body>404 Not Found</body></html>"
        private const val PROXY_CONFLICT_HTML = "<html><body>409 Conflict</body></html>"

        /** 중계 구간이 낸 JSON 오류 봉투. type은 있지만 포트원이 쓰는 값이 아님. */
        private val FOREIGN_NOT_FOUND_JSON =
            """
            {"type": "NotFound", "message": "Not Found"}
            """.trimIndent()

        private val ALREADY_PAID_JSON =
            """
            {"type": "ALREADY_PAID", "message": "이미 결제된 건입니다."}
            """.trimIndent()

        private val CANCEL_SUCCEEDED_JSON =
            """
            {
              "cancellation": {
                "status": "SUCCEEDED",
                "id": "cancel-1",
                "pgCancellationId": "pgcancel-1",
                "totalAmount": 10000,
                "taxFreeAmount": 0,
                "vatAmount": 909,
                "reason": "고객 변심",
                "requestedAt": "2026-08-20T11:00:00Z",
                "cancelledAt": "2026-08-20T11:00:00Z"
              }
            }
            """.trimIndent()

        private val PG_PROVIDER_ERROR_JSON =
            """
            {"type": "PG_PROVIDER", "message": "PG사 처리 중 오류", "pgCode": "F113", "pgMessage": "일시적 오류"}
            """.trimIndent()
    }
}
