package com.aechak.api.payment.request

import com.aechak.application.payment.usecase.command.PreparePaymentCommand
import com.aechak.domain.payment.enums.PaymentMethod

data class PreparePaymentRequest(
    val method: PaymentMethod,
) {
    fun toCommand(
        buyerId: Long,
        orderGroupPublicId: String,
    ): PreparePaymentCommand =
        PreparePaymentCommand(
            buyerId = buyerId,
            orderGroupPublicId = orderGroupPublicId,
            method = method,
        )
}
