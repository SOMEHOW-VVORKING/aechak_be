package com.aechak.domain.payment

import com.aechak.common.error.BusinessException
import com.aechak.domain.payment.enums.PaymentMethod
import com.aechak.domain.payment.enums.PaymentStatus
import com.aechak.domain.payment.error.PaymentErrorCode
import java.time.LocalDateTime

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
    val cancelledAmount: Long,
    val cancelRequestedAt: LocalDateTime?,
    val version: Int,
) {
    /** 이미 승인이면 그대로 반환 — 콜백·웹훅이 같은 승인을 겹쳐 전달해도 멱등. FAILED에서의 승인은 재시도 성공 */
    fun approve(
        pgTxId: String?,
        realPaidAmount: Long,
    ): Payment {
        if (status == PaymentStatus.APPROVED) return this
        // 취소가 시작된 결제에 뒤늦게 도착한 승인 — 받아들이면 취소 원장이 승인액으로 되돌아간다
        if (status.isApprovedOrLater) {
            throw BusinessException(PaymentErrorCode.PAYMENT_STATE_NOT_SUPPORTED)
        }
        return copyWith(
            status = PaymentStatus.APPROVED,
            transactionId = pgTxId,
            realPaidAmount = realPaidAmount,
            failureCode = null, // 마지막 시도의 실패 기록은 승인으로 종결됨
            cancellableAmount = realPaidAmount,
        )
    }

    /** 실패는 최종 상태가 아니라 마지막 시도의 결과 기록 — 재실패면 코드를 갱신한다. 승인 뒤 실패 통보는 이상 사건이라 차단 */
    fun fail(failureCode: String?): Payment {
        if (status.isApprovedOrLater) {
            throw BusinessException(PaymentErrorCode.PAYMENT_ALREADY_PAID)
        }
        return copyWith(status = PaymentStatus.FAILED, failureCode = failureCode)
    }

    /**
     * 취소 선점 — 상태만 CANCEL_REQUESTED로 돌리고 금액 원장은 건드리지 않는다.
     * 같은 행을 두 요청이 선점하면 낙관적 락이 심판이고, 진 쪽은 이 단계에서 걸린다.
     */
    fun requestCancel(
        amount: Long,
        at: LocalDateTime = LocalDateTime.now(),
    ): Payment {
        if (status == PaymentStatus.CANCEL_REQUESTED) {
            throw BusinessException(PaymentErrorCode.PAYMENT_CANCEL_IN_PROGRESS)
        }
        val remaining = cancellableAmount
        if ((status != PaymentStatus.APPROVED && status != PaymentStatus.PARTIAL_CANCELLED) || remaining == null || remaining <= 0) {
            throw BusinessException(PaymentErrorCode.PAYMENT_NOT_CANCELLABLE)
        }
        if (amount <= 0) {
            throw BusinessException(PaymentErrorCode.INVALID_PAYMENT_AMOUNT, detail = "취소 금액은 1원 이상이어야 합니다.")
        }
        if (amount > remaining) {
            throw BusinessException(PaymentErrorCode.CANCEL_AMOUNT_EXCEEDS_CANCELLABLE)
        }
        return copyWith(status = PaymentStatus.CANCEL_REQUESTED, cancelRequestedAt = at)
    }

    /**
     * PG가 취소를 확정했을 때의 원장 반영. 줄이는 것은 취소가능액뿐이다 —
     * 원승인액(targetAmount·realPaidAmount)을 깎으면 대사가 모든 취소 건을 금액 불일치로 떨어뜨린다.
     */
    fun completeCancel(cancelledAmount: Long): Payment {
        if (status != PaymentStatus.CANCEL_REQUESTED) {
            throw BusinessException(PaymentErrorCode.PAYMENT_NOT_CANCELLABLE)
        }
        val remaining = cancellableAmount ?: throw BusinessException(PaymentErrorCode.PAYMENT_NOT_CANCELLABLE)
        if (cancelledAmount <= 0) {
            throw BusinessException(PaymentErrorCode.INVALID_PAYMENT_AMOUNT, detail = "취소 금액은 1원 이상이어야 합니다.")
        }
        if (cancelledAmount > remaining) {
            throw BusinessException(PaymentErrorCode.CANCEL_AMOUNT_EXCEEDS_CANCELLABLE)
        }
        val left = remaining - cancelledAmount
        return copyWith(
            status = if (left == 0L) PaymentStatus.CANCELLED else PaymentStatus.PARTIAL_CANCELLED,
            cancellableAmount = left,
            cancelledAmount = this.cancelledAmount + cancelledAmount,
        )
    }

    /**
     * 취소 선점 해제 — 누적 취소액이 있으면 부분취소로, 없으면 승인으로 되돌린다.
     * 선점 중이 아니면 그대로 반환한다: 실패 처리 경로에서 원래 예외를 가리지 않게 한다.
     */
    fun failCancel(): Payment {
        if (status != PaymentStatus.CANCEL_REQUESTED) return this
        return copyWith(status = if (cancelledAmount > 0) PaymentStatus.PARTIAL_CANCELLED else PaymentStatus.APPROVED)
    }

    private fun copyWith(
        status: PaymentStatus = this.status,
        transactionId: String? = this.transactionId,
        realPaidAmount: Long? = this.realPaidAmount,
        failureCode: String? = this.failureCode,
        cancellableAmount: Long? = this.cancellableAmount,
        cancelledAmount: Long = this.cancelledAmount,
        cancelRequestedAt: LocalDateTime? = this.cancelRequestedAt,
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
            cancelledAmount = cancelledAmount,
            cancelRequestedAt = cancelRequestedAt,
            version = version,
        )

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
                cancelledAmount = 0L,
                cancelRequestedAt = null,
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
            cancelledAmount: Long,
            cancelRequestedAt: LocalDateTime?,
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
                cancelledAmount = cancelledAmount,
                cancelRequestedAt = cancelRequestedAt,
                version = version,
            )
    }
}
