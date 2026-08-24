package com.aechak.application.order.cart.usecase.command

data class UpdateCartItemCommand(
    val buyerId: Long,
    val cartItemId: Long,
    val quantity: Int?,
    val optionCombinationId: Long?,
)
