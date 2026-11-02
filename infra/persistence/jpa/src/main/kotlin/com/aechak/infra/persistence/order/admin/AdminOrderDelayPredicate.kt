package com.aechak.infra.persistence.order.admin

import com.aechak.application.order.admin.support.OrderDelayDeadlines
import com.aechak.domain.order.order.QOrder
import com.aechak.domain.order.order.enums.OrderStatus
import com.aechak.domain.order.shipment.QShipment
import com.aechak.domain.order.shipment.enums.ShipmentType
import com.querydsl.core.types.dsl.BooleanExpression
import com.querydsl.core.types.dsl.DateTimeExpression
import com.querydsl.core.types.dsl.Expressions
import com.querydsl.jpa.JPAExpressions
import java.time.LocalDateTime

/** 발송 전 상태 집합을 도메인 판정에서 뽑는다 — 집합을 여기 다시 적으면 상태가 늘 때 한쪽만 늘어난다. */
private val AWAITING_DISPATCH_STATUSES = OrderStatus.entries.filter { it.isAwaitingDispatch() }.toSet()

/** 지연 술어 전용 배송 별칭 — 다른 조인과 같은 이름을 쓰면 상관 서브쿼리가 바깥 행을 못 가린다. */
private val delayShipment = QShipment("delayShipment")

/**
 * 지연 신호 술어. `OrderDelayDeadlines.reasonFor`와 같은 두 축을 SQL로 쓴 것이다 —
 * 목록 행의 신호(코틀린 판정)와 지표 카드의 건수(이 술어)가 같은 건수를 말해야 한다.
 */
internal fun delayedPredicate(
    order: QOrder,
    deadlines: OrderDelayDeadlines,
): BooleanExpression =
    order.status
        .`in`(AWAITING_DISPATCH_STATUSES)
        .and(order.createdAt.lt(deadlines.dispatchDeadline))
        .or(
            order.status
                .eq(OrderStatus.SHIPPING)
                .and(deliveryClock(order).lt(deadlines.deliveryDeadline)),
        )

/**
 * 배송 지연 시계의 시작점 — 주문에 붙은 가장 늦은 **출고** 발송 시각, 없으면 주문 시각.
 * 회수 송장은 역방향이라 시계를 되돌리지 않게 모수에서 뺀다.
 * `OrderDelayDeadlines`의 코틀린 판정이 고르는 값과 같아야 카드와 목록이 같은 건수를 말한다.
 */
private fun deliveryClock(order: QOrder): DateTimeExpression<LocalDateTime> =
    Expressions.dateTimeTemplate(
        LocalDateTime::class.java,
        "coalesce({0}, {1})",
        JPAExpressions
            .select(delayShipment.dispatchedAt.max())
            .from(delayShipment)
            .where(
                delayShipment.order.id.eq(order.id),
                delayShipment.shipmentType.`in`(ShipmentType.OUTBOUND),
            ),
        order.createdAt,
    )

internal fun awaitingDispatchPredicate(order: QOrder): BooleanExpression = order.status.`in`(AWAITING_DISPATCH_STATUSES)
