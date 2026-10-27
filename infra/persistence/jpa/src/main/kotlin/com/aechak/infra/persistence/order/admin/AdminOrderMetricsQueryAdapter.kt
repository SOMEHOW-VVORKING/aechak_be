package com.aechak.infra.persistence.order.admin

import com.aechak.application.order.admin.port.AdminOrderMetricsCondition
import com.aechak.application.order.admin.port.AdminOrderMetricsQueryPort
import com.aechak.application.order.admin.port.view.AdminOrderMetricsCountView
import com.aechak.domain.order.order.QOrder
import com.aechak.domain.order.order.QOrderItem
import com.aechak.domain.order.order.enums.OrderStatus
import com.querydsl.core.types.Predicate
import com.querydsl.core.types.Projections
import com.querydsl.core.types.dsl.BooleanExpression
import com.querydsl.core.types.dsl.Expressions
import com.querydsl.core.types.dsl.NumberExpression
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

private val order = QOrder.order
private val orderItem = QOrderItem.orderItem

/**
 * 어드민 모니터링 카드의 집계. 건수 셋은 조건부 합계로 주문 테이블을 한 번만 훑는다 —
 * 카드마다 쿼리를 따로 돌면 화면 하나에 집계가 넷 붙는다.
 *
 * 금액만 쿼리를 나눈다. 품목 테이블을 조인해야 상품금액이 나오는데, 건수 쿼리에 조인을 섞으면
 * 주문 행이 품목 수만큼 복제돼 건수와 배송비 합이 모두 부풀려진다.
 */
@Repository
class AdminOrderMetricsQueryAdapter(
    private val queryFactory: JPAQueryFactory,
) : AdminOrderMetricsQueryPort {
    override fun countMetrics(condition: AdminOrderMetricsCondition): AdminOrderMetricsCountView =
        queryFactory
            .select(
                Projections.constructor(
                    AdminOrderMetricsCountView::class.java,
                    countWhere(awaitingDispatchPredicate(order)),
                    countWhere(order.status.eq(OrderStatus.SHIPPING)),
                    countWhere(delayedPredicate(order, condition.deadlines)),
                    sumOf(order.sellerShippingFee.subtract(order.allocatedCouponDiscount)),
                ),
            ).from(order)
            .where(*filters(condition))
            .fetchOne()!!

    override fun sumProductAmount(condition: AdminOrderMetricsCondition): Long =
        queryFactory
            .select(sumOf(orderItem.unitPriceSnapshot.multiply(orderItem.quantity)))
            .from(order)
            .join(order._items, orderItem)
            .where(*filters(condition))
            .fetchOne() ?: 0L

    private fun filters(condition: AdminOrderMetricsCondition): Array<Predicate?> =
        arrayOf(
            order.status.`in`(condition.countedStatuses),
            condition.sellerId?.let { order.sellerId.eq(it) },
            condition.orderedFrom?.let { order.createdAt.goe(it) },
            condition.orderedToExclusive?.let { order.createdAt.lt(it) },
        )

    /** 주문이 하나도 없는 조회 범위에서는 집계가 NULL이다 — 0은 쿼리에서 못박는다 */
    private fun countWhere(predicate: BooleanExpression): NumberExpression<Long> =
        sumOf(
            Expressions
                .cases()
                .`when`(predicate)
                .then(1L)
                .otherwise(0L),
        )

    private fun sumOf(value: NumberExpression<Long>): NumberExpression<Long> =
        Expressions.numberTemplate(Long::class.javaObjectType, "coalesce(sum({0}), 0)", value)
}
