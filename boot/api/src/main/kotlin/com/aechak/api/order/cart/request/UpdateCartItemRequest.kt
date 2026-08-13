package com.aechak.api.order.cart.request

import com.aechak.application.order.cart.usecase.command.UpdateCartItemCommand
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min

data class UpdateCartItemRequest(
    @field:Min(value = CartItemConstraints.QUANTITY_MIN.toLong(), message = "수량은 {value}개 이상이어야 합니다.")
    @field:Max(value = CartItemConstraints.QUANTITY_MAX.toLong(), message = "수량은 {value}개를 넘을 수 없습니다.")
    val quantity: Int? = null,
    val optionCombinationId: Long? = null,
) {
    fun toCommand(
        buyerId: Long,
        cartItemId: Long,
    ): UpdateCartItemCommand =
        UpdateCartItemCommand(
            buyerId = buyerId,
            cartItemId = cartItemId,
            quantity = quantity,
            optionCombinationId = optionCombinationId,
        )
}
