package com.aechak.api.order.group.request

import com.aechak.application.order.usecase.command.CreateOrderGroupCommand
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotEmpty

data class CreateOrderGroupRequest(
    @field:NotEmpty(message = "주문할 장바구니 항목을 선택해 주세요.")
    val cartItemIds: List<Long>,
    val deliveryAddressId: Long,
    @field:Min(value = 0, message = "적립금 사용액은 0원 이상이어야 합니다.")
    val usedPoint: Long,
    @field:Min(value = 0, message = "결제 예정 금액은 0원 이상이어야 합니다.")
    val expectedFinalAmount: Long,
) {
    fun toCommand(
        buyerId: Long,
        idempotencyKey: String,
    ): CreateOrderGroupCommand =
        CreateOrderGroupCommand(
            buyerId = buyerId,
            cartItemIds = cartItemIds,
            deliveryAddressId = deliveryAddressId,
            usedPoint = usedPoint,
            expectedFinalAmount = expectedFinalAmount,
            idempotencyKey = idempotencyKey,
        )
}
