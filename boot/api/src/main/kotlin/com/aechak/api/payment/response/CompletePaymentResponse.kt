package com.aechak.api.payment.response

import com.aechak.application.payment.usecase.result.CompletePaymentResult
import com.aechak.application.payment.usecase.result.CompletePaymentStatus

data class CompletePaymentResponse(
    val status: CompletePaymentStatus,
    val orderGroupId: String,
    val finalPaymentAmount: Long,
    val usedPoint: Long,
    val failureCode: String?,
) {
    companion object {
        fun from(result: CompletePaymentResult) =
            CompletePaymentResponse(
                status = result.status,
                orderGroupId = result.orderGroupId,
                finalPaymentAmount = result.finalPaymentAmount,
                usedPoint = result.usedPoint,
                failureCode = result.failureCode,
            )
    }
}
