package com.aechak.infra.persistence.order

import com.aechak.application.order.port.OrderListCondition
import com.aechak.application.order.port.OrderListQueryPort
import com.aechak.application.order.port.OrderStatusFilter
import com.aechak.application.order.port.view.OrderDetailView
import com.aechak.application.order.port.view.OrderGroupView
import com.aechak.application.order.port.view.OrderLineView
import com.aechak.application.order.port.view.SellerOrderView
import com.aechak.domain.order.group.QOrderGroup
import com.aechak.domain.order.order.QOrder
import com.aechak.domain.order.order.QOrderItem
import com.aechak.domain.order.order.enums.OrderStatus
import com.aechak.domain.product.option.QOptionCombination
import com.aechak.domain.product.version.QProductVersion
import com.querydsl.core.types.Predicate
import com.querydsl.core.types.Projections
import com.querydsl.jpa.JPAExpressions
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Repository

private val orderGroup = QOrderGroup.orderGroup
private val order = QOrder.order
private val orderItem = QOrderItem.orderItem
private val productVersion = QProductVersion.productVersion
private val optionCombination = QOptionCombination.optionCombination

@Repository
class OrderListQueryAdapter(
    private val queryFactory: JPAQueryFactory,
) : OrderListQueryPort {
    override fun findGroupPage(condition: OrderListCondition): List<OrderGroupView> =
        queryFactory
            .select(groupProjection())
            .from(orderGroup)
            .where(
                orderGroup.buyerId.eq(condition.buyerId),
                hasOrderIn(condition.filter),
                keyset(condition.lastId),
            ).orderBy(orderGroup.id.desc())
            .limit(condition.limit.toLong())
            .fetch()

    override fun countGroups(
        buyerId: Long,
        filter: OrderStatusFilter,
    ): Long =
        queryFactory
            .select(orderGroup.count())
            .from(orderGroup)
            .where(orderGroup.buyerId.eq(buyerId), hasOrderIn(filter))
            .fetchOne() ?: 0L

    override fun findSellerOrdersByGroupIds(groupIds: Collection<Long>): List<SellerOrderView> {
        if (groupIds.isEmpty()) return emptyList()
        return queryFactory
            .select(
                Projections.constructor(
                    SellerOrderView::class.java,
                    order.orderGroup.id,
                    order.id,
                    order.publicId,
                    order.sellerNameSnapshot,
                    order.status,
                ),
            ).from(order)
            .where(order.orderGroup.id.`in`(groupIds), order.status.ne(OrderStatus.PENDING_PAYMENT))
            .orderBy(order.orderGroup.id.desc(), order.id.asc())
            .fetch()
    }

    override fun findLinesByOrderIds(orderIds: Collection<Long>): List<OrderLineView> {
        if (orderIds.isEmpty()) return emptyList()
        return queryFactory
            .select(
                Projections.constructor(
                    OrderLineView::class.java,
                    order.id,
                    productVersion.nameSnapshot,
                    productVersion.thumbnailKeySnapshot,
                    optionCombination.name,
                    orderItem.quantity,
                    orderItem.unitPriceSnapshot,
                    orderItem.itemStatus,
                ),
            ).from(order)
            .join(order._items, orderItem)
            .join(productVersion)
            .on(productVersion.id.eq(orderItem.productVersionId))
            .join(optionCombination)
            .on(optionCombination.id.eq(orderItem.optionCombinationId))
            .where(order.id.`in`(orderIds))
            .orderBy(order.id.asc(), orderItem.id.asc())
            .fetch()
    }

    override fun findOwnedDetail(
        orderPublicId: String,
        buyerId: Long,
    ): OrderDetailView? =
        queryFactory
            .select(
                Projections.constructor(
                    OrderDetailView::class.java,
                    order.id,
                    order.publicId,
                    orderGroup.publicId,
                    orderGroup.createdAt,
                    order.status,
                    order.sellerNameSnapshot,
                    order.sellerShippingFee,
                    orderGroup.totalProductAmount,
                    orderGroup.totalShippingFee,
                    orderGroup.couponDiscountAmount,
                    orderGroup.usedPoint,
                    orderGroup.finalPaymentAmount,
                ),
            ).from(order)
            .join(order.orderGroup, orderGroup)
            .where(
                order.publicId.eq(orderPublicId),
                orderGroup.buyerId.eq(buyerId),
                order.status.ne(OrderStatus.PENDING_PAYMENT),
            ).fetchOne()

    /**
     * 세그먼트 필터와 결제대기 제외를 한 EXISTS로 겸함. ALL의 상태 집합에도 결제대기가 없기 때문.
     * 결제 전 주문이 테이블에 남는 모델이라 이 조건이 빠지면 미결제 건이 그대로 샘.
     */
    private fun hasOrderIn(filter: OrderStatusFilter): Predicate {
        val sub = QOrder("subOrder")
        return JPAExpressions
            .selectOne()
            .from(sub)
            .where(sub.orderGroup.id.eq(orderGroup.id), sub.status.`in`(filter.statuses))
            .exists()
    }

    private fun keyset(lastId: Long?): Predicate? = lastId?.let { orderGroup.id.lt(it) }

    private fun groupProjection() =
        Projections.constructor(
            OrderGroupView::class.java,
            orderGroup.id,
            orderGroup.publicId,
            orderGroup.createdAt,
            orderGroup.totalProductAmount,
            orderGroup.totalShippingFee,
            orderGroup.couponDiscountAmount,
            orderGroup.usedPoint,
            orderGroup.finalPaymentAmount,
        )
}
