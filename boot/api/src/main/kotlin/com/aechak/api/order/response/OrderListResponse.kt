package com.aechak.api.order.response

import com.aechak.application.order.usecase.result.OrderGroupItemResult
import com.aechak.application.order.usecase.result.OrderLineItemResult
import com.aechak.application.order.usecase.result.OrderListResult
import com.aechak.application.order.usecase.result.SellerOrderItemResult
import java.time.OffsetDateTime
import java.time.ZoneId

data class OrderListResponse(
    val orders: List<OrderGroupItemResponse>,
    val totalCount: Long?,
    val nextCursor: String?,
    val hasNext: Boolean,
) {
    companion object {
        fun from(result: OrderListResult): OrderListResponse =
            OrderListResponse(
                orders = result.page.items.map(OrderGroupItemResponse::from),
                totalCount = result.page.totalCount,
                nextCursor = result.page.nextCursor,
                hasNext = result.page.hasNext,
            )
    }
}

data class OrderGroupItemResponse(
    val orderGroupId: String,
    val orderedAt: OffsetDateTime,
    val totalProductAmount: Long,
    val totalShippingFee: Long,
    val couponDiscountAmount: Long,
    val usedPoint: Long,
    val finalPaymentAmount: Long,
    val sellerOrders: List<SellerOrderItemResponse>,
) {
    companion object {
        fun from(result: OrderGroupItemResult): OrderGroupItemResponse =
            OrderGroupItemResponse(
                orderGroupId = result.orderGroupId,
                orderedAt = result.orderedAt.atZone(ZoneId.systemDefault()).toOffsetDateTime(),
                totalProductAmount = result.totalProductAmount,
                totalShippingFee = result.totalShippingFee,
                couponDiscountAmount = result.couponDiscountAmount,
                usedPoint = result.usedPoint,
                finalPaymentAmount = result.finalPaymentAmount,
                sellerOrders = result.sellerOrders.map(SellerOrderItemResponse::from),
            )
    }
}

data class SellerOrderItemResponse(
    val orderId: String,
    val sellerName: String?,
    val status: String,
    val itemCount: Int,
    val representativeItem: OrderLineItemResponse?,
) {
    companion object {
        fun from(result: SellerOrderItemResult): SellerOrderItemResponse =
            SellerOrderItemResponse(
                orderId = result.orderId,
                sellerName = result.sellerName,
                status = result.status,
                itemCount = result.itemCount,
                representativeItem = result.representativeItem?.let(OrderLineItemResponse::from),
            )
    }
}

data class OrderLineItemResponse(
    val productName: String,
    val thumbnailUrl: String?,
    val optionName: String,
    val quantity: Int,
    val unitPrice: Long,
    val itemStatus: String,
) {
    companion object {
        fun from(result: OrderLineItemResult): OrderLineItemResponse =
            OrderLineItemResponse(
                productName = result.productName,
                thumbnailUrl = result.thumbnailUrl,
                optionName = result.optionName,
                quantity = result.quantity,
                unitPrice = result.unitPrice,
                itemStatus = result.itemStatus,
            )
    }
}
