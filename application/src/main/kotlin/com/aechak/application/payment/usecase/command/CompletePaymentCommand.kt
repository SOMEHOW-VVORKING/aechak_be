package com.aechak.application.payment.usecase.command

data class CompletePaymentCommand(
    val buyerId: Long,
    val orderGroupPublicId: String,
)
