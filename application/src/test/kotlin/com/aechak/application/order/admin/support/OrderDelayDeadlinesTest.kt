package com.aechak.application.order.admin.support

import com.aechak.application.order.admin.usecase.result.AdminOrderDelayReason
import com.aechak.domain.order.order.enums.OrderStatus
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 지연 판정 단위 테스트. 두 경계 시각이 주는 판정이 상태별로 정확히 갈리는지를 고정한다 —
 * 이 판정과 집계 SQL이 같은 건수를 말해야 카드와 목록이 어긋나지 않는다(SQL 쪽은 통합 테스트가 대조).
 */
class OrderDelayDeadlinesTest {
    private val dispatchDeadline = LocalDateTime.of(2026, 10, 25, 0, 0)
    private val deliveryDeadline = LocalDateTime.of(2026, 10, 21, 0, 0)
    private val deadlines = OrderDelayDeadlines(dispatchDeadline, deliveryDeadline)

    @Test
    fun `발송 전 상태가 발송 기한보다 먼저 주문됐으면 발송 지연이다`() {
        listOf(OrderStatus.PAID, OrderStatus.PREPARING, OrderStatus.DISPATCH_PENDING).forEach { status ->
            assertEquals(
                AdminOrderDelayReason.DISPATCH_OVERDUE,
                deadlines.reasonFor(status, dispatchDeadline.minusSeconds(1)),
                "$status 는 발송 전 구간이라 발송 기한을 넘기면 지연이다",
            )
        }
    }

    @Test
    fun `발송 기한 경계의 주문은 아직 지연이 아니다`() {
        assertNull(
            deadlines.reasonFor(OrderStatus.PAID, dispatchDeadline),
            "경계는 포함하지 않는다 — 포함하면 기한 당일 주문이 즉시 지연으로 집계된다",
        )
    }

    @Test
    fun `배송중은 더 긴 배송 기한으로 판정한다`() {
        assertNull(
            deadlines.reasonFor(OrderStatus.SHIPPING, dispatchDeadline.minusSeconds(1)),
            "발송 기한을 넘겼더라도 송장이 붙었으면 발송 지연이 아니다",
        )
        assertEquals(
            AdminOrderDelayReason.DELIVERY_OVERDUE,
            deadlines.reasonFor(OrderStatus.SHIPPING, deliveryDeadline.minusSeconds(1)),
            "배송중인 채로 배송 기한을 넘기면 배송 정체다",
        )
    }

    @Test
    fun `끝난 주문은 아무리 오래됐어도 지연이 아니다`() {
        listOf(
            OrderStatus.DELIVERED,
            OrderStatus.PURCHASE_CONFIRMED,
            OrderStatus.CANCELLED,
        ).forEach { status ->
            assertNull(
                deadlines.reasonFor(status, deliveryDeadline.minusYears(1)),
                "$status 는 운영자가 더 할 일이 없는 상태라 지연 신호를 띄우면 카드가 영구히 빨갛다",
            )
        }
    }
}
