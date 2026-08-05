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
    /** 담긴 수량의 합계. 품목 종류 수가 아님. */
    val cartItemCount: Int,
) {
    companion object {
        /** itemStatus가 ACTIVE 고정인 이유는 나머지 상태가 담기 단계에서 전부 막혀 여기까지 못 오기 때문임. */
        fun from(
            cartItem: CartItem,
            catalogItem: CartCatalogItemView,
            cartItemCount: Int,
        ): AddCartItemResult =
            AddCartItemResult(
                cartItemId = cartItem.id,
                productId = catalogItem.productPublicId,
                optionCombinationId = cartItem.optionCombinationId,
                quantity = cartItem.quantity,
                itemStatus = CartItemStatus.ACTIVE,
                cartItemCount = cartItemCount,
            )
    }
}
