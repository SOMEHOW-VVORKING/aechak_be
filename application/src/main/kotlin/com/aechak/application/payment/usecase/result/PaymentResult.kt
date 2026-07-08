package com.aechak.application.payment.usecase.result

import com.aechak.domain.payment.Payment

/** UseCase 반환 전용 모델 골격 — 엔티티 반환 금지. 규칙은 user 템플릿(UserResult) 참조. */
data class PaymentResult(
    val paymentId: Long,
    // TODO: 기능 확정 시 필드 추가
) {
    companion object {
        fun from(payment: Payment): PaymentResult = PaymentResult(payment.id)
    }
}
