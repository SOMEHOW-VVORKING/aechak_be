package com.aechak.application.dashboard.seller

import com.aechak.application.dashboard.seller.usecase.result.SellerDashboardSummaryResult
import com.aechak.application.order.claim.usecase.result.SellerClaimMetricsResult
import com.aechak.application.order.seller.usecase.result.SellerOrderMetricsResult
import com.aechak.application.product.product.usecase.result.SellerProductStatusCountsResult
import com.aechak.application.settlement.seller.port.view.SellerSettlementSummaryView
import com.aechak.application.settlement.seller.usecase.result.SellerSettlementSummaryResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SellerDashboardSummaryResultTest {
    @Test
    fun `직전 기간이 없으면 지표의 previousValue가 전부 null이다`() {
        val result = assemble(previous = false)

        assertNull(result.metrics.newOrders.previousValue)
        assertNull(result.metrics.awaitingShipment.previousValue)
        assertNull(result.metrics.shippingDelayed.previousValue)
        assertNull(result.metrics.cancelRequests.previousValue)
        assertNull(result.metrics.returnRequests.previousValue)
        assertNull(result.metrics.exchangeRequests.previousValue)
    }

    @Test
    fun `직전 기간 값은 지표마다 짝이 맞게 실린다`() {
        val result = assemble(previous = true)

        assertEquals(12L to 9L, result.metrics.newOrders.value to result.metrics.newOrders.previousValue)
        assertEquals(5L to 4L, result.metrics.awaitingShipment.value to result.metrics.awaitingShipment.previousValue)
        assertEquals(1L to 0L, result.metrics.shippingDelayed.value to result.metrics.shippingDelayed.previousValue)
        assertEquals(2L to 1L, result.metrics.cancelRequests.value to result.metrics.cancelRequests.previousValue)
        assertEquals(3L to 2L, result.metrics.returnRequests.value to result.metrics.returnRequests.previousValue)
        assertEquals(0L to 7L, result.metrics.exchangeRequests.value to result.metrics.exchangeRequests.previousValue)
    }

    @Test
    fun `미응답 문의만 미지원이고 0은 값이 아니라 미지원 표시다`() {
        val result = assemble(previous = true)

        assertFalse(result.metrics.unansweredInquiries.supported)
        assertEquals(0L, result.metrics.unansweredInquiries.value)
        assertNull(result.metrics.unansweredInquiries.previousValue)
        assertTrue(result.metrics.newOrders.supported)
        assertTrue(result.metrics.cancelRequests.supported)
    }

    @Test
    fun `정산 예정 금액은 대기와 보류의 합이다`() {
        val summary = SellerSettlementSummaryResult.from(SellerSettlementSummaryView(pendingAmount = 420_000L, onHoldAmount = 48_000L))

        assertEquals(468_000L, summary.totalAmount)
        assertEquals(420_000L, summary.pendingAmount)
        assertEquals(48_000L, summary.onHoldAmount)
    }

    private fun assemble(previous: Boolean): SellerDashboardSummaryResult =
        SellerDashboardSummaryResult.of(
            orders = SellerOrderMetricsResult(newOrders = 12L, awaitingShipment = 5L, shippingDelayed = 1L),
            previousOrders =
                if (previous) SellerOrderMetricsResult(newOrders = 9L, awaitingShipment = 4L, shippingDelayed = 0L) else null,
            claims = SellerClaimMetricsResult(cancelRequests = 2L, returnRequests = 3L, exchangeRequests = 0L),
            previousClaims =
                if (previous) SellerClaimMetricsResult(cancelRequests = 1L, returnRequests = 2L, exchangeRequests = 7L) else null,
            settlement = SellerSettlementSummaryResult(totalAmount = 0L, pendingAmount = 0L, onHoldAmount = 0L),
            productStatuses =
                SellerProductStatusCountsResult(awaitingInspection = 0L, rejected = 0L, suspended = 0L, soldOut = 0L),
        )
}
