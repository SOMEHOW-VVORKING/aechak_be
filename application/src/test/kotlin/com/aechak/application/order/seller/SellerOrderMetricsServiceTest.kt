package com.aechak.application.order.seller

import com.aechak.application.order.seller.port.SellerOrderMetricsCondition
import com.aechak.application.order.seller.port.SellerOrderMetricsQueryPort
import com.aechak.application.order.seller.port.view.SellerOrderMetricsView
import com.aechak.application.order.seller.service.SellerOrderMetricsService
import com.aechak.application.order.seller.usecase.query.SellerOrderMetricsQuery
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class SellerOrderMetricsServiceTest {
    private val capturing = CapturingPort()

    @Test
    fun `발송 기한은 명세가 실어 온 기준 시각에서 센다`() {
        val at = LocalDateTime.of(2020, 3, 10, 9, 0)

        service(slaDays = 3L).countMetrics(query(at))

        assertEquals(
            LocalDateTime.of(2020, 3, 7, 9, 0),
            capturing.condition!!.dispatchDeadline,
            "집계가 스스로 시계를 뜨면 과거 기준 시각을 넘겨도 지금 기준으로 판정된다",
        )
    }

    @Test
    fun `발송 기한 일수는 설정값을 따른다`() {
        val at = LocalDateTime.of(2026, 10, 19, 13, 30)

        service(slaDays = 7L).countMetrics(query(at))

        assertEquals(at.minusDays(7), capturing.condition!!.dispatchDeadline)
    }

    @Test
    fun `주문일 범위와 셀러는 명세의 값을 그대로 넘긴다`() {
        val at = LocalDateTime.of(2026, 10, 19, 13, 30)
        val from = at.minusDays(30)

        service(slaDays = 3L).countMetrics(query(at, orderedFrom = from, orderedToExclusive = at))

        assertEquals(from, capturing.condition!!.orderedFrom)
        assertEquals(at, capturing.condition!!.orderedToExclusive)
        assertEquals(42L, capturing.condition!!.sellerId)
    }

    private fun service(slaDays: Long) = SellerOrderMetricsService(capturing, slaDays)

    private fun query(
        at: LocalDateTime,
        orderedFrom: LocalDateTime? = null,
        orderedToExclusive: LocalDateTime? = null,
    ) = SellerOrderMetricsQuery(
        sellerId = 42L,
        at = at,
        orderedFrom = orderedFrom,
        orderedToExclusive = orderedToExclusive,
    )

    private class CapturingPort : SellerOrderMetricsQueryPort {
        var condition: SellerOrderMetricsCondition? = null

        override fun countMetrics(condition: SellerOrderMetricsCondition): SellerOrderMetricsView {
            this.condition = condition
            return SellerOrderMetricsView(newOrders = 0L, awaitingShipment = 0L, shippingDelayed = 0L)
        }
    }
}
