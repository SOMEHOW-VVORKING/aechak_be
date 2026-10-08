package com.aechak.application.order.usecase.command

/**
 * 주문그룹 생성 입력. 금액은 담지 않는다 — 구속력 있는 계산은 서버가 한다.
 * expectedFinalAmount는 계산 입력이 아니라 FE가 표시한 금액과의 대조용.
 */
data class CreateOrderGroupCommand(
    val buyerId: Long,
    val cartItemIds: List<Long>,
    val deliveryAddressId: Long,
    val usedPoint: Long,
    val expectedFinalAmount: Long,
    val idempotencyKey: String,
)
