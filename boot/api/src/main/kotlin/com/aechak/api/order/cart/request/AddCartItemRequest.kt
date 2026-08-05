package com.aechak.api.order.cart.request

import com.aechak.application.order.cart.usecase.command.AddCartItemCommand
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.NotNull

// 원시 타입은 필드 생략 시 0으로 새서 @NotNull이 못 잡음. 전부 nullable로 받음.
// 멱등 키는 본문 필드가 아니라 Idempotency-Key 헤더 방식으로 후속 도입.
data class AddCartItemRequest(
    @field:NotNull(message = "옵션 조합 ID는 필수입니다.")
    val optionCombinationId: Long?,
    @field:NotNull(message = "수량은 필수입니다.")
    @field:Max(value = 99, message = "수량은 {value}개를 넘을 수 없습니다.")
    val quantity: Int?,
) {
    fun toCommand(buyerId: Long): AddCartItemCommand =
        AddCartItemCommand(
            buyerId = buyerId,
            optionCombinationId = optionCombinationId!!,
            quantity = quantity!!,
        )
}
