package com.aechak.infra.persistence.order

import com.aechak.application.order.port.OrderItemReviewQueryPort
import com.aechak.application.order.port.view.OrderItemReviewView
import com.aechak.domain.order.group.QOrderGroup
import com.aechak.domain.order.order.QOrder
import com.aechak.domain.order.order.QOrderItem
import com.querydsl.core.types.Projections
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Repository

private val order = QOrder.order
private val orderItem = QOrderItem.orderItem
private val orderGroup = QOrderGroup.orderGroup

@Repository
class OrderItemReviewQueryAdapter(
    private val queryFactory: JPAQueryFactory,
) : OrderItemReviewQueryPort {
    override fun findOrderItemForReview(
        orderItemId: Long,
        buyerId: Long,
    ): OrderItemReviewView? =
        queryFactory
            .select(
                Projections.constructor(
                    OrderItemReviewView::class.java,
                    orderItem.id,
                    order.status,
                    orderItem.itemStatus,
                    order.purchaseConfirmedAt,
                    orderItem.productId,
                    orderItem.optionNameSnapshot,
                ),
            ).from(order)
            .join(order._items, orderItem)
            .join(order.orderGroup, orderGroup)
            .where(
                orderItem.id.eq(orderItemId),
                orderGroup.buyerId.eq(buyerId),
            ).fetchOne()
}
