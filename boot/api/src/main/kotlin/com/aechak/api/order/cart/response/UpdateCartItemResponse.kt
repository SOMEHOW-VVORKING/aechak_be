package com.aechak.api.order.cart.response

import com.aechak.application.order.cart.usecase.result.UpdateCartItemResult

data class UpdateCartItemResponse(
    val cartItemId: Long,
    val quantity: Int,
    val optionCombinationId: Long,
    val merged: Boolean,
    val cartItemCount: Int,
) {
    companion object {
        fun from(result: UpdateCartItemResult): UpdateCartItemResponse =
            UpdateCartItemResponse(
                cartItemId = result.cartItemId,
                quantity = result.quantity,
                optionCombinationId = result.optionCombinationId,
                merged = result.merged,
                cartItemCount = result.cartItemCount,
            )
    }
}
