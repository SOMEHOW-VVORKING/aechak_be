package com.aechak.application.order.usecase.result

import com.aechak.application.order.port.view.OrderItemForReviewView
import com.aechak.domain.order.order.enums.OrderItemStatus
import com.aechak.domain.order.order.enums.OrderStatus
import java.time.LocalDateTime

/** 리뷰 작성 자격 판정에 필요한 주문품목 정보 */
data class OrderItemForReviewResult(
    val orderItemId: Long,
    val isPurchaseConfirmed: Boolean,
    val isItemReviewable: Boolean,
    val purchaseConfirmedAt: LocalDateTime?,
    val productId: Long,
    val optionNameSnapshot: String,
) {
    companion object {
        fun from(view: OrderItemForReviewView): OrderItemForReviewResult =
            OrderItemForReviewResult(
                orderItemId = view.orderItemId,
                isPurchaseConfirmed = view.orderStatus == OrderStatus.PURCHASE_CONFIRMED,
                isItemReviewable = view.itemStatus == OrderItemStatus.ORDERED,
                purchaseConfirmedAt = view.purchaseConfirmedAt,
                productId = view.productId,
                optionNameSnapshot = view.optionNameSnapshot,
            )
    }
}
