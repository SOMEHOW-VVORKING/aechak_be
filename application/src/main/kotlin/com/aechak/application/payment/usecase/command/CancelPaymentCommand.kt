package com.aechak.application.payment.usecase.command

import com.aechak.application.payment.port.PaymentCancelRequester

/** amount 가 null 이면 남은 취소가능액 전액을 취소한다 */
data class CancelPaymentCommand(
    val orderGroupId: Long,
    val amount: Long?,
    val reason: String,
    val requester: PaymentCancelRequester,
)
