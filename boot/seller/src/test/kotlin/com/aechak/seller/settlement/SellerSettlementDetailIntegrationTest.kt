package com.aechak.seller.settlement

import com.jayway.jsonpath.JsonPath
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * 셀러 정산 상세 통합 테스트. 금액 분해·요율 스냅샷·품목 내역과 "요약 = 내역의 합" 불변식, 소유권 판정을 고정한다.
 * 깨지면 셀러가 보는 지급 예정액이 저장된 원장과 어긋나거나 남의 정산이 새는 것이다.
 */
class SellerSettlementDetailIntegrationTest : SellerSettlementIntegrationTestBase() {
    @Test
    fun `상세는 금액 분해와 생성 시점 요율 스냅샷을 내려 준다`() {
        val order = seedSettledOrder()

        requestDetail(order.orderPublicId)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.orderId").value(order.orderPublicId))
            .andExpect(jsonPath("$.data.settlementMonth").value("2026-06"))
            .andExpect(jsonPath("$.data.confirmedAt").value("2026-06-18T10:00:00"))
            .andExpect(jsonPath("$.data.expectedPayoutDate").value("2026-06-23"))
            .andExpect(jsonPath("$.data.productAmount").value(ONE_LINE_PRODUCT_AMOUNT))
            .andExpect(jsonPath("$.data.shippingFee").value(BASE_SHIPPING_FEE.toInt()))
            .andExpect(jsonPath("$.data.couponBurden").value(0))
            .andExpect(jsonPath("$.data.platformFeeAmount").value(ONE_LINE_COMMISSION))
            .andExpect(jsonPath("$.data.pgFeeAmount").value(ONE_LINE_PG_FEE))
            .andExpect(jsonPath("$.data.feeAmount").value(ONE_LINE_FEE_AMOUNT))
            .andExpect(jsonPath("$.data.refundDeduction").value(0))
            .andExpect(jsonPath("$.data.finalAmount").value(ONE_LINE_FINAL_AMOUNT))
            .andExpect(jsonPath("$.data.status").value("pending"))
            // 화면이 "플랫폼 수수료 8%"를 하드코딩하지 않도록 자릿수까지 그대로 내려 준다
            .andExpect(content().string(containsString("\"platformFeeRate\":0.0800")))
            .andExpect(content().string(containsString("\"pgFeeRate\":0.0280")))
    }

    @Test
    fun `품목 내역은 주문품목별 금액과 상품명·수량을 담는다`() {
        val order = seedSettledOrder(lines = TWO_LINES)
        val settlements = settlementsOf(order.orderId)

        requestDetail(order.orderPublicId)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.items.length()").value(2))
            .andExpect(jsonPath("$.data.items[0].orderItemId").value(order.orderItemIds[0].toInt()))
            .andExpect(jsonPath("$.data.items[0].productName").value(order.productNames[0]))
            .andExpect(jsonPath("$.data.items[0].quantity").value(2))
            .andExpect(jsonPath("$.data.items[0].grossAmount").value(settlements[0].grossAmount.toInt()))
            .andExpect(jsonPath("$.data.items[0].shippingFee").value(settlements[0].shippingFee.toInt()))
            .andExpect(jsonPath("$.data.items[0].commission").value(settlements[0].commission.toInt()))
            .andExpect(jsonPath("$.data.items[0].pgFee").value(settlements[0].pgFee.toInt()))
            .andExpect(jsonPath("$.data.items[0].netAmount").value(settlements[0].netAmount.toInt()))
            .andExpect(jsonPath("$.data.items[1].productName").value(order.productNames[1]))
            .andExpect(jsonPath("$.data.items[1].quantity").value(1))
            .andExpect(jsonPath("$.data.items[1].netAmount").value(settlements[1].netAmount.toInt()))
    }

    @Test
    fun `요약 금액은 품목 내역의 합과 같다`() {
        val order = seedSettledOrder(lines = TWO_LINES, sellerCouponBurden = 300L)
        deductRefund(order.settlementIds.first(), 1_000L)

        val body =
            requestDetail(order.orderPublicId)
                .andExpect(status().isOk)
                .andReturn()
                .response.contentAsString

        assertEquals(itemSum(body, "grossAmount"), summary(body, "productAmount"), "상품금액 합")
        assertEquals(itemSum(body, "shippingFee"), summary(body, "shippingFee"), "배송비 합")
        assertEquals(itemSum(body, "commission"), summary(body, "platformFeeAmount"), "플랫폼 수수료 합")
        assertEquals(itemSum(body, "pgFee"), summary(body, "pgFeeAmount"), "PG 수수료 합")
        assertEquals(itemSum(body, "sellerCouponBurden"), summary(body, "couponBurden"), "쿠폰 부담 합")
        assertEquals(itemSum(body, "refundDeduction"), summary(body, "refundDeduction"), "환불 차감 합")
        assertEquals(itemSum(body, "netAmount"), summary(body, "finalAmount"), "지급 예정액 합")
        assertEquals(
            summary(body, "platformFeeAmount") + summary(body, "pgFeeAmount"),
            summary(body, "feeAmount"),
            "수수료 합계는 두 수수료의 합",
        )
    }

    @Test
    fun `목록 행과 상세의 요약 금액이 같다`() {
        val order = seedSettledOrder(lines = TWO_LINES)
        deductRefund(order.settlementIds.last(), 500L)

        val listBody =
            requestList()
                .andExpect(status().isOk)
                .andReturn()
                .response.contentAsString
        val detailBody =
            requestDetail(order.orderPublicId)
                .andExpect(status().isOk)
                .andReturn()
                .response.contentAsString

        listOf("productAmount", "feeAmount", "finalAmount").forEach { field ->
            assertEquals(
                number(listBody, "$.data.settlements[0].$field"),
                summary(detailBody, field),
                field,
            )
        }
    }

    @Test
    fun `환불 차감이 반영된 지급 예정액을 그대로 내려 준다`() {
        val order = seedSettledOrder()
        deductRefund(order.settlementIds.single(), 2_000L)

        requestDetail(order.orderPublicId)
            .andExpect(jsonPath("$.data.refundDeduction").value(2_000))
            .andExpect(jsonPath("$.data.finalAmount").value(ONE_LINE_FINAL_AMOUNT - 2_000))
            .andExpect(jsonPath("$.data.productAmount").value(ONE_LINE_PRODUCT_AMOUNT))
    }

    @Test
    fun `취소된 정산 행은 상세에서도 빠진다`() {
        val order = seedSettledOrder(lines = TWO_LINES)
        cancelSettlement(order.settlementIds.last())
        val remaining = settlementsOf(order.orderId).single { it.id == order.settlementIds.first() }

        requestDetail(order.orderPublicId)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.items.length()").value(1))
            .andExpect(jsonPath("$.data.items[0].orderItemId").value(order.orderItemIds[0].toInt()))
            .andExpect(jsonPath("$.data.productAmount").value(remaining.grossAmount.toInt()))
            .andExpect(jsonPath("$.data.finalAmount").value(remaining.netAmount.toInt()))
    }

    @Test
    fun `남의 정산은 403으로 막는다`() {
        val (otherSellerId, otherToken) = openOtherSeller()
        val mine = seedSettledOrder()
        val theirs = seedSettledOrder(sellerId = otherSellerId)

        requestDetail(theirs.orderPublicId)
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.errorCode").value(70003))

        requestDetail(mine.orderPublicId, asToken = otherToken)
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.errorCode").value(70003))
    }

    @Test
    fun `없는 주문번호와 정산이 전부 취소된 주문은 404로 막는다`() {
        val cancelled = seedSettledOrder()
        cancelSettlement(cancelled.settlementIds.single())

        requestDetail("01JZZZZZZZZZZZZZZZZZZZZZZZ")
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.errorCode").value(70002))

        requestDetail(cancelled.orderPublicId)
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.errorCode").value(70002))
    }

    private fun summary(
        body: String,
        field: String,
    ): Long = number(body, "$.data.$field")

    private fun itemSum(
        body: String,
        field: String,
    ): Long = JsonPath.read<List<Number>>(body, "$.data.items[*].$field").sumOf { it.toLong() }

    private fun number(
        body: String,
        path: String,
    ): Long = JsonPath.read<Number>(body, path).toLong()
}
