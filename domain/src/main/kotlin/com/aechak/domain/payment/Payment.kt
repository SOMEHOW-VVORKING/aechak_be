package com.aechak.domain.payment

import com.aechak.common.error.BusinessException
import com.aechak.domain.payment.enums.PaymentMethod
import com.aechak.domain.payment.enums.PaymentStatus
import com.aechak.domain.payment.error.PaymentErrorCode

class Payment private constructor(
    val id: Long,
    val orderGroupId: Long,
    val paymentId: String,
    val method: PaymentMethod,
    val targetAmount: Long,
    val status: PaymentStatus,
    val transactionId: String?,
    val realPaidAmount: Long?,
    val failureCode: String?,
    val cancellableAmount: Long?,
    val version: Int,
) {
    companion object {
        fun prepare(
            orderGroupId: Long,
            paymentId: String,
            method: PaymentMethod,
            targetAmount: Long,
        ): Payment {
            if (targetAmount < 0) {
                throw BusinessException(PaymentErrorCode.INVALID_PAYMENT_AMOUNT)
            }
            return Payment(
                id = 0L,
                orderGroupId = orderGroupId,
                paymentId = paymentId,
                method = method,
                targetAmount = targetAmount,
                status = PaymentStatus.PENDING,
                transactionId = null,
                realPaidAmount = null,
                failureCode = null,
                cancellableAmount = null,
                version = 0,
            )
        }

        fun restore(
            id: Long,
            orderGroupId: Long,
            paymentId: String,
            method: PaymentMethod,
            targetAmount: Long,
            status: PaymentStatus,
            transactionId: String?,
            realPaidAmount: Long?,
            failureCode: String?,
            cancellableAmount: Long?,
            version: Int,
        ): Payment =
            Payment(
                id = id,
                orderGroupId = orderGroupId,
                paymentId = paymentId,
                method = method,
                targetAmount = targetAmount,
                status = status,
                transactionId = transactionId,
                realPaidAmount = realPaidAmount,
                failureCode = failureCode,
                cancellableAmount = cancellableAmount,
                version = version,
            )
    }
}
