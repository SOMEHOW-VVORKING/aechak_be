package com.aechak.application.payment.usecase.result

import com.aechak.domain.payment.Payment
import com.aechak.domain.payment.enums.PaymentStatus

/**
 * 취소 요청의 결과. 실패는 여기 담지 않고 예외로 던진다.
 */
enum class CancelPaymentStatus {
    /** PG가 취소를 확정했고 원장도 반영됨 */
    COMPLETED,

    /** PG가 비동기로 접수만 함 — 원장은 그대로이고 반영은 PG 통보·대사가 확인한다 */
    REQUESTED,
}

data class CancelPaymentResult(
    val status: CancelPaymentStatus,
    val paymentStatus: PaymentStatus,
    /** 이번 요청으로 확정된 금액. 접수만 된 건은 0 */
    val cancelledAmount: Long,
    /** 반영 후 남은 취소가능액 */
    val cancellableAmount: Long,
) {
    companion object {
        fun of(
            status: CancelPaymentStatus,
            payment: Payment,
            cancelledAmount: Long,
        ): CancelPaymentResult =
            CancelPaymentResult(
                status = status,
                paymentStatus = payment.status,
                cancelledAmount = cancelledAmount,
                cancellableAmount = payment.cancellableAmount ?: 0L,
            )
    }
}
