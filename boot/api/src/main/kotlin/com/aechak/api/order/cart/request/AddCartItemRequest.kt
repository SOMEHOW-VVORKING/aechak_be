package com.aechak.api.order.cart.request

import com.aechak.application.order.cart.usecase.command.AddCartItemCommand
import jakarta.validation.constraints.Max

// 멱등 키는 본문 필드가 아니라 Idempotency-Key 헤더 방식으로 후속 도입.
data class AddCartItemRequest(
    val optionCombinationId: Long,
    // 하한은 도메인이 50200으로 판정함. 요청 검증은 상한만.
    @field:Max(value = 99, message = "수량은 {value}개를 넘을 수 없습니다.")
    val quantity: Int,
) {
    fun toCommand(buyerId: Long): AddCartItemCommand =
        AddCartItemCommand(
            buyerId = buyerId,
            optionCombinationId = optionCombinationId,
            quantity = quantity,
        )
}
