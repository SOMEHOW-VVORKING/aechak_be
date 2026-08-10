package com.aechak.application.order.cart.usecase.command

// toEntity 없음. 신규와 누적 분기가 Cart.addItem 안이라 미리 만들면 누적 경로에서 버려짐.
data class AddCartItemCommand(
    val buyerId: Long,
    val optionCombinationId: Long,
    val quantity: Int,
)
