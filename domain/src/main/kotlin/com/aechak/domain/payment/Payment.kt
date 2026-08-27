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
    /** 이미 승인이면 그대로 반환 — 콜백·웹훅이 같은 승인을 겹쳐 전달해도 멱등. FAILED에서의 승인은 재시도 성공 */
    fun approve(
        pgTxId: String?,
        realPaidAmount: Long,
    ): Payment {
        if (status == PaymentStatus.APPROVED) return this
        return Payment(
            id = id,
            orderGroupId = orderGroupId,
            paymentId = paymentId,
            method = method,
            targetAmount = targetAmount,
            status = PaymentStatus.APPROVED,
            transactionId = pgTxId,
            realPaidAmount = realPaidAmount,
            failureCode = null, // 마지막 시도의 실패 기록은 승인으로 종결됨
            cancellableAmount = realPaidAmount,
            version = version,
        )
    }

    /** 실패는 최종 상태가 아니라 마지막 시도의 결과 기록 — 재실패면 코드를 갱신한다. 승인 뒤 실패 통보는 이상 사건이라 차단 */
    fun fail(failureCode: String?): Payment {
        if (status == PaymentStatus.APPROVED) {
            throw BusinessException(PaymentErrorCode.PAYMENT_ALREADY_PAID)
        }
        return Payment(
            id = id,
            orderGroupId = orderGroupId,
            paymentId = paymentId,
            method = method,
            targetAmount = targetAmount,
            status = PaymentStatus.FAILED,
            transactionId = transactionId,
            realPaidAmount = realPaidAmount,
            failureCode = failureCode,
            cancellableAmount = cancellableAmount,
            version = version,
        )
    }

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
