package com.aechak.application.order.usecase.result

import com.aechak.application.order.port.view.OrderDetailView
import com.aechak.application.order.port.view.OrderLineView
import java.time.LocalDateTime

data class OrderDetailResult(
    val orderId: String,
    val orderGroupId: String,
    val orderedAt: LocalDateTime,
    val status: String,
    val sellerName: String?,
    val items: List<OrderLineItemResult>,
    val payment: OrderPaymentResult,
) {
    companion object {
        fun of(
            view: OrderDetailView,
            lines: List<OrderLineView>,
            resolveThumbnail: (String?) -> String?,
        ): OrderDetailResult =
            OrderDetailResult(
                orderId = view.orderPublicId,
                orderGroupId = view.orderGroupPublicId,
                orderedAt = view.orderedAt,
                status = view.status.name,
                sellerName = view.sellerName,
                items = lines.map { OrderLineItemResult.of(it, resolveThumbnail(it.thumbnailKey)) },
                payment = OrderPaymentResult.from(view),
            )
    }
}

/** sellerShippingFee만 이 셀러 몫이고 나머지는 결제 단위인 주문그룹 값. */
data class OrderPaymentResult(
    val sellerShippingFee: Long,
    val totalProductAmount: Long,
    val totalShippingFee: Long,
    val couponDiscountAmount: Long,
    val usedPoint: Long,
    val finalPaymentAmount: Long,
) {
    companion object {
        fun from(view: OrderDetailView): OrderPaymentResult =
            OrderPaymentResult(
                sellerShippingFee = view.sellerShippingFee,
                totalProductAmount = view.totalProductAmount,
                totalShippingFee = view.totalShippingFee,
                couponDiscountAmount = view.couponDiscountAmount,
                usedPoint = view.usedPoint,
                finalPaymentAmount = view.finalPaymentAmount,
            )
    }
}
