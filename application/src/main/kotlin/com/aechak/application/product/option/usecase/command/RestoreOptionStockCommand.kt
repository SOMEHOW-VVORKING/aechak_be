package com.aechak.application.product.option.usecase.command

/**
 * 재고 복원 명세. 멱등키가 없어 이 호출 자체는 몇 번이고 재고를 늘린다 —
 * 한 번만 늘어나게 막는 것은 호출측의 상태 전이 선점이다.
 */
data class RestoreOptionStockCommand(
    val optionCombinationId: Long,
    val quantity: Int,
)
