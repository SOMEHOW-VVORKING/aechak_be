package com.aechak.application.payment.usecase.command

/** PG가 확정한 취소 금액을 원장에 반영하는 명세. */
data class ApplyPaymentCancelCommand(
    val orderGroupId: Long,
    val cancelledAmount: Long,
)
