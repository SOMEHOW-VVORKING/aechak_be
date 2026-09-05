package com.aechak.application.order.usecase.result

import com.aechak.application.order.port.view.OrderGroupView
import com.aechak.application.order.port.view.OrderLineView
import com.aechak.application.order.port.view.SellerOrderView
import com.aechak.application.support.CursorPageResult
import java.time.LocalDateTime

data class OrderListResult(
    val page: CursorPageResult<OrderGroupItemResult>,
)

data class OrderGroupItemResult(
    val orderGroupId: String,
    val orderedAt: LocalDateTime,
    val totalProductAmount: Long,
    val totalShippingFee: Long,
    val couponDiscountAmount: Long,
    val usedPoint: Long,
    val finalPaymentAmount: Long,
    val sellerOrders: List<SellerOrderItemResult>,
) {
    companion object {
        fun of(
            view: OrderGroupView,
            sellerOrders: List<SellerOrderItemResult>,
        ): OrderGroupItemResult =
            OrderGroupItemResult(
                orderGroupId = view.publicId,
                orderedAt = view.orderedAt,
                totalProductAmount = view.totalProductAmount,
                totalShippingFee = view.totalShippingFee,
                couponDiscountAmount = view.couponDiscountAmount,
                usedPoint = view.usedPoint,
                finalPaymentAmount = view.finalPaymentAmount,
                sellerOrders = sellerOrders,
            )
    }
}

data class SellerOrderItemResult(
    val orderId: String,
    val sellerName: String?,
    val status: String,
    val itemCount: Int,
    val representativeItem: OrderLineItemResult?,
) {
    companion object {
        fun of(
            view: SellerOrderView,
            lines: List<OrderLineView>,
            resolveThumbnail: (String?) -> String?,
        ): SellerOrderItemResult =
            SellerOrderItemResult(
                orderId = view.orderPublicId,
                sellerName = view.sellerName,
                status = view.status.name,
                itemCount = lines.size,
                representativeItem = lines.firstOrNull()?.let { OrderLineItemResult.of(it, resolveThumbnail(it.thumbnailKey)) },
            )
    }
}
