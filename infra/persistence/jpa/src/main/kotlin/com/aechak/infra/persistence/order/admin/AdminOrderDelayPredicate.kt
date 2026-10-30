package com.aechak.infra.persistence.order.admin

import com.aechak.application.order.admin.support.OrderDelayDeadlines
import com.aechak.domain.order.order.QOrder
import com.aechak.domain.order.order.enums.OrderStatus
import com.querydsl.core.types.dsl.BooleanExpression

/** 발송 전 상태 집합을 도메인 판정에서 뽑는다 — 집합을 여기 다시 적으면 상태가 늘 때 한쪽만 늘어난다. */
private val AWAITING_DISPATCH_STATUSES = OrderStatus.entries.filter { it.isAwaitingDispatch() }.toSet()

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
                .and(order.createdAt.lt(deadlines.deliveryDeadline)),
        )

internal fun awaitingDispatchPredicate(order: QOrder): BooleanExpression = order.status.`in`(AWAITING_DISPATCH_STATUSES)
