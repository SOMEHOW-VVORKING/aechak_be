package com.aechak.api.order.cart.request

import com.aechak.application.order.cart.usecase.command.DeleteCartItemsCommand

data class DeleteCartItemsRequest(
    val cartItemIds: List<Long>,
) {
    fun toCommand(buyerId: Long): DeleteCartItemsCommand =
        DeleteCartItemsCommand(
            buyerId = buyerId,
            cartItemIds = cartItemIds.toSet(),
        )
}
