package com.aechak.api.order.cart.request

import com.aechak.application.order.cart.usecase.command.AddCartItemCommand
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min

// 멱등 키는 본문 필드가 아니라 Idempotency-Key 헤더 방식으로 후속 도입.
data class AddCartItemRequest(
    val optionCombinationId: Long,
    @field:Min(value = CartItemConstraints.QUANTITY_MIN.toLong(), message = "수량은 {value}개 이상이어야 합니다.")
    @field:Max(value = CartItemConstraints.QUANTITY_MAX.toLong(), message = "수량은 {value}개를 넘을 수 없습니다.")
    val quantity: Int,
) {
    fun toCommand(buyerId: Long): AddCartItemCommand =
        AddCartItemCommand(
            buyerId = buyerId,
            optionCombinationId = optionCombinationId,
            quantity = quantity,
        )
}
