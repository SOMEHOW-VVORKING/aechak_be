package com.aechak.application.order.usecase.command

/** 구매자 구매확정 요청. 소유권은 buyerId로 판정하고 타인 주문은 없는 것으로 본다. */
data class ConfirmPurchaseCommand(
    val buyerId: Long,
    val orderPublicId: String,
)
