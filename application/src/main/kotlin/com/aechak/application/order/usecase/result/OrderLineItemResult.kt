package com.aechak.application.order.usecase.result

import com.aechak.application.order.port.view.OrderLineView
import com.aechak.domain.order.order.enums.OrderItemStatus

data class OrderLineItemResult(
    val orderItemId: Long,
    val productId: String,
    val productName: String,
    val thumbnailUrl: String?,
    val optionName: String,
    val quantity: Int,
    val unitPrice: Long,
    val itemStatus: OrderItemStatus,
) {
    companion object {
        fun of(
            view: OrderLineView,
            thumbnailUrl: String?,
        ): OrderLineItemResult =
            OrderLineItemResult(
                orderItemId = view.orderItemId,
                productId = view.productPublicId,
                productName = view.productName,
                thumbnailUrl = thumbnailUrl,
                optionName = view.optionName,
                quantity = view.quantity,
                unitPrice = view.unitPrice,
                itemStatus = view.itemStatus,
            )
    }
}
