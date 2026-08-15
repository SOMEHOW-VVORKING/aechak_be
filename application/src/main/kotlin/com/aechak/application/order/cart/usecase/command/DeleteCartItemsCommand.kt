package com.aechak.application.order.cart.usecase.command

data class DeleteCartItemsCommand(
    val buyerId: Long,
    val cartItemIds: Set<Long>,
)
