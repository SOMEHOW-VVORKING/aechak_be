package com.aechak.application.order.cart.usecase.result

import com.aechak.application.order.cart.port.view.CartCatalogItemView
import com.aechak.domain.order.cart.CartItem
import com.aechak.domain.order.cart.enums.CartItemStatus

data class AddCartItemResult(
    val cartItemId: Long,
    /** products.public_id (ULID) */
    val productId: String,
    val optionCombinationId: Long,
    val quantity: Int,
    val itemStatus: CartItemStatus,
) {
    companion object {
        fun from(
            cartItem: CartItem,
            catalogItem: CartCatalogItemView,
            itemStatus: CartItemStatus,
        ): AddCartItemResult =
            AddCartItemResult(
                cartItemId = cartItem.id,
                productId = catalogItem.productPublicId,
                optionCombinationId = cartItem.optionCombinationId,
                quantity = cartItem.quantity,
                itemStatus = itemStatus,
            )
    }
}
