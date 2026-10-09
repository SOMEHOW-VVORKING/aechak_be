package com.aechak.application.payment.usecase.command

import com.aechak.domain.payment.enums.PaymentMethod

data class PreparePaymentCommand(
    val buyerId: Long,
    val orderGroupPublicId: String,
    val method: PaymentMethod,
)
