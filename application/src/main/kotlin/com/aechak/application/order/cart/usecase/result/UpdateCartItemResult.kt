package com.aechak.application.order.cart.usecase.result

import com.aechak.domain.order.cart.CartItem

data class UpdateCartItemResult(
    /** 병합이 일어나면 요청 경로의 id가 아니라 살아남은 목적지 줄의 id임. */
    val cartItemId: Long,
    val quantity: Int,
    val optionCombinationId: Long,
    val merged: Boolean,
    /** 담긴 수량의 합계. 품목 종류 수가 아님. */
    val cartItemCount: Int,
) {
    companion object {
        fun from(
            survivor: CartItem,
            merged: Boolean,
            cartItemCount: Int,
        ): UpdateCartItemResult =
            UpdateCartItemResult(
                cartItemId = survivor.id,
                quantity = survivor.quantity,
                optionCombinationId = survivor.optionCombinationId,
                merged = merged,
                cartItemCount = cartItemCount,
            )
    }
}
