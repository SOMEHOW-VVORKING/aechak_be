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
                deadlines.reasonFor(status, dispatchDeadline.minusSeconds(1), null),
                "$status 는 발송 전 구간이라 발송 기한을 넘기면 지연이다",
            )
        }
    }

    @Test
    fun `발송 기한 경계의 주문은 아직 지연이 아니다`() {
        assertNull(
            deadlines.reasonFor(OrderStatus.PAID, dispatchDeadline, null),
            "경계는 포함하지 않는다 — 포함하면 기한 당일 주문이 즉시 지연으로 집계된다",
        )
    }

    /** 송장이 붙기 전 상태에 발송 시각이 실릴 일은 없지만, 실려도 발송 축의 판정을 흔들지 않아야 한다. */
    @Test
    fun `발송 전 상태의 판정은 발송 시각을 보지 않는다`() {
        assertEquals(
            AdminOrderDelayReason.DISPATCH_OVERDUE,
            deadlines.reasonFor(OrderStatus.PREPARING, dispatchDeadline.minusSeconds(1), LocalDateTime.of(2026, 10, 30, 0, 0)),
        )
    }

    /**
     * 배송중의 시계는 **발송 시각**부터 센다. 주문 시각에서 재면 발송이 늦은 주문이 송장을 받는
     * 순간 발송 축에서 빠지고 배송 축에 그대로 걸려, 운영자가 셀러를 독촉해야 할 건에 택배사를 쫓는다.
     */
    @Test
    fun `배송중은 발송 시각부터 배송 기한을 센다`() {
        assertNull(
            deadlines.reasonFor(
                OrderStatus.SHIPPING,
                deliveryDeadline.minusYears(1),
                deliveryDeadline.plusSeconds(1),
            ),
            "주문이 아무리 오래됐어도 방금 발송됐으면 배송 정체가 아니다",
        )
        assertEquals(
            AdminOrderDelayReason.DELIVERY_OVERDUE,
            deadlines.reasonFor(
                OrderStatus.SHIPPING,
                LocalDateTime.of(2026, 10, 30, 0, 0),
                deliveryDeadline.minusSeconds(1),
            ),
            "발송한 지 기한을 넘겼으면 주문이 최근이어도 배송 정체다",
        )
    }

    @Test
    fun `배송 기한 경계에 발송된 주문은 아직 지연이 아니다`() {
        assertNull(
            deadlines.reasonFor(OrderStatus.SHIPPING, deliveryDeadline.minusYears(1), deliveryDeadline),
            "경계는 포함하지 않는다 — 발송 축과 같은 규칙이어야 두 축이 같은 뜻으로 읽힌다",
        )
    }

    /** 송장만 있고 발송 기록이 없는 배송중 주문의 시계를 멈추면 두 축 어디에도 안 잡혀 영구히 안 보인다. */
    @Test
    fun `발송 시각이 없는 배송중은 주문 시각으로 센다`() {
        assertEquals(
            AdminOrderDelayReason.DELIVERY_OVERDUE,
            deadlines.reasonFor(OrderStatus.SHIPPING, deliveryDeadline.minusSeconds(1), null),
        )
        assertNull(
            deadlines.reasonFor(OrderStatus.SHIPPING, deliveryDeadline, null),
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
                deadlines.reasonFor(status, deliveryDeadline.minusYears(1), deliveryDeadline.minusYears(1)),
                "$status 는 운영자가 더 할 일이 없는 상태라 지연 신호를 띄우면 카드가 영구히 빨갛다",
            )
        }
    }
}
