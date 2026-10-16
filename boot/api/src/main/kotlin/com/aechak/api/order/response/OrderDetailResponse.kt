package com.aechak.api.order.response

import com.aechak.application.order.usecase.result.OrderDetailResult
import com.aechak.application.order.usecase.result.OrderPaymentResult
import java.time.OffsetDateTime
import java.time.ZoneId

data class OrderDetailResponse(
    val orderId: String,
    val orderGroupId: String,
    val orderedAt: OffsetDateTime,
    val status: String,
    val sellerName: String?,
    val items: List<OrderLineItemResponse>,
    val payment: OrderPaymentResponse,
) {
    companion object {
        fun from(result: OrderDetailResult): OrderDetailResponse =
            OrderDetailResponse(
                orderId = result.orderId,
                orderGroupId = result.orderGroupId,
                orderedAt = result.orderedAt.atZone(ZoneId.systemDefault()).toOffsetDateTime(),
                status = result.status,
                sellerName = result.sellerName,
                items = result.items.map(OrderLineItemResponse::from),
                payment = OrderPaymentResponse.from(result.payment),
            )
    }
}

data class OrderPaymentResponse(
    val sellerShippingFee: Long,
    val totalProductAmount: Long,
    val totalShippingFee: Long,
    val couponDiscountAmount: Long,
    val usedPoint: Long,
    val finalPaymentAmount: Long,
) {
    companion object {
        fun from(result: OrderPaymentResult): OrderPaymentResponse =
            OrderPaymentResponse(
                sellerShippingFee = result.sellerShippingFee,
                totalProductAmount = result.totalProductAmount,
                totalShippingFee = result.totalShippingFee,
                couponDiscountAmount = result.couponDiscountAmount,
                usedPoint = result.usedPoint,
                finalPaymentAmount = result.finalPaymentAmount,
            )
    }
}
