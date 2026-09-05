package com.aechak.application.order.usecase.result

import com.aechak.application.order.port.view.OrderLineView

data class OrderLineItemResult(
    val productName: String,
    val thumbnailUrl: String?,
    val optionName: String,
    val quantity: Int,
    val unitPrice: Long,
    val itemStatus: String,
) {
    companion object {
        fun of(
            view: OrderLineView,
            thumbnailUrl: String?,
        ): OrderLineItemResult =
            OrderLineItemResult(
                productName = view.productName,
                thumbnailUrl = thumbnailUrl,
                optionName = view.optionName,
                quantity = view.quantity,
                unitPrice = view.unitPrice,
                itemStatus = view.itemStatus.name,
            )
    }
}
