package com.aechak.api.order.cart.response

import com.aechak.application.order.cart.usecase.result.AddCartItemResult

data class AddCartItemResponse(
    val cartItemId: Long,
    val productId: String,
    val optionCombinationId: Long,
    val quantity: Int,
    val itemStatus: String,
    val cartItemCount: Int,
) {
    companion object {
        fun from(result: AddCartItemResult): AddCartItemResponse =
            AddCartItemResponse(
                cartItemId = result.cartItemId,
                productId = result.productId,
                optionCombinationId = result.optionCombinationId,
                quantity = result.quantity,
                itemStatus = result.itemStatus.name,
                cartItemCount = result.cartItemCount,
            )
    }
}
