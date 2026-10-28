package com.aechak.application.payment.usecase.result

import com.aechak.domain.payment.Payment

data class PreparePaymentResult(
    val paymentId: String,
    val targetAmount: Long,
) {
    companion object {
        fun from(payment: Payment): PreparePaymentResult =
            PreparePaymentResult(
                paymentId = payment.paymentId,
                targetAmount = payment.targetAmount,
            )
    }
}
