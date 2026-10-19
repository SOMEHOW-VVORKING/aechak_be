package com.aechak.application.product.option.usecase.command

/** 재고 차감 명세 — 주문 생성처럼 자산을 확보하는 흐름이 넘긴다 */
data class DeductOptionStockCommand(
    val optionCombinationId: Long,
    val quantity: Int,
)
