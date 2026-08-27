package com.aechak.infra.persistence.order

import com.aechak.application.order.port.OrderItemForReviewQueryPort
import com.aechak.application.order.port.view.OrderItemForReviewView
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
class OrderItemForReviewQueryAdapter(
    private val queryFactory: JPAQueryFactory,
) : OrderItemForReviewQueryPort {
    override fun findOrderItemForReview(
        orderItemId: Long,
        buyerId: Long,
    ): OrderItemForReviewView? =
        queryFactory
            .select(
                Projections.constructor(
                    OrderItemForReviewView::class.java,
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
