package com.aechak.api.order.cart.response

import com.aechak.application.order.cart.usecase.result.CartItemCountResult

data class CartItemCountResponse(
    val cartItemCount: Int,
) {
    companion object {
        fun from(result: CartItemCountResult): CartItemCountResponse =
            CartItemCountResponse(
                cartItemCount = result.cartItemCount,
            )
    }
}
