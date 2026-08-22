package com.aechak.api.payment.response

import com.aechak.api.payment.config.PaymentStoreProperties
import com.aechak.application.payment.usecase.result.PreparePaymentResult

data class PreparePaymentResponse(
    val paymentId: String,
    val targetAmount: Long,
    val storeId: String,
    val channelKey: String,
) {
    companion object {
        fun of(
            result: PreparePaymentResult,
            store: PaymentStoreProperties,
        ) = PreparePaymentResponse(
            paymentId = result.paymentId,
            targetAmount = result.targetAmount,
            storeId = store.id,
            channelKey = store.channelKey,
        )
    }
}
