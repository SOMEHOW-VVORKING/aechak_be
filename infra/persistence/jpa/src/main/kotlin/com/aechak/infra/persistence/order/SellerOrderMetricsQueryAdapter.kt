package com.aechak.infra.persistence.order

import com.aechak.application.order.seller.port.SellerOrderMetricsCondition
import com.aechak.application.order.seller.port.SellerOrderMetricsQueryPort
import com.aechak.application.order.seller.port.view.SellerOrderMetricsView
import com.aechak.domain.order.group.QOrderGroup
import com.aechak.domain.order.order.QOrder
import com.aechak.domain.order.order.enums.OrderStatus
import com.querydsl.core.types.Predicate
import com.querydsl.core.types.Projections
import com.querydsl.core.types.dsl.BooleanExpression
import com.querydsl.core.types.dsl.CaseBuilder
import com.querydsl.core.types.dsl.Expressions
import com.querydsl.core.types.dsl.NumberExpression
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

private val order = QOrder.order
private val orderGroup = QOrderGroup.orderGroup

/** 아직 발송하지 않은 셀러 몫 — 송장을 등록하면 배송중으로 전이돼 이 집합에서 빠진다. */
private val AWAITING_DISPATCH_STATUSES = setOf(OrderStatus.PREPARING, OrderStatus.DISPATCH_PENDING)

/**
 * 셀러 대시보드의 주문 지표를 조건부 합계로 한 번에 센다 — 지표마다 쿼리를 따로 돌면
 * 화면 하나에 집계 쿼리가 지표 수만큼 붙는다.
 * 주문일은 주문그룹 생성 시각이고, 결제대기 주문은 어느 지표에도 들어가지 않는다.
 */
@Repository
class SellerOrderMetricsQueryAdapter(
    private val queryFactory: JPAQueryFactory,
) : SellerOrderMetricsQueryPort {
    override fun countMetrics(condition: SellerOrderMetricsCondition): SellerOrderMetricsView =
        queryFactory
            .select(
                Projections.constructor(
                    SellerOrderMetricsView::class.java,
                    countWhere(order.status.eq(OrderStatus.PAID)),
                    countWhere(order.status.`in`(AWAITING_DISPATCH_STATUSES)),
                    countWhere(
                        order.status
                            .`in`(AWAITING_DISPATCH_STATUSES)
                            .and(orderGroup.createdAt.lt(condition.dispatchDeadline)),
                    ),
                ),
            ).from(order)
            .join(order.orderGroup, orderGroup)
            .where(
                order.sellerId.eq(condition.sellerId),
                orderedFrom(condition.orderedFrom),
                orderedBefore(condition.orderedToExclusive),
            ).fetchOne()!!

    /** 주문이 없는 셀러에서는 집계가 NULL이다 — 0은 쿼리에서 못박고 투영은 값이 있다고만 전제한다 */
    private fun countWhere(predicate: BooleanExpression): NumberExpression<Long> =
        Expressions.numberTemplate(
            Long::class.javaObjectType,
            "coalesce(sum({0}), 0)",
            CaseBuilder().`when`(predicate).then(1L).otherwise(0L),
        )

    private fun orderedFrom(from: LocalDateTime?): Predicate? = from?.let { orderGroup.createdAt.goe(it) }

    private fun orderedBefore(toExclusive: LocalDateTime?): Predicate? = toExclusive?.let { orderGroup.createdAt.lt(it) }
}
