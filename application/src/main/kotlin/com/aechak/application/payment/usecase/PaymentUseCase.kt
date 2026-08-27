package com.aechak.application.payment.usecase

import com.aechak.application.payment.usecase.command.CompletePaymentCommand
import com.aechak.application.payment.usecase.command.PreparePaymentCommand
import com.aechak.application.payment.usecase.result.CompletePaymentResult
import com.aechak.application.payment.usecase.result.PreparePaymentResult

interface PaymentUseCase {
    /** 결제 행을 만들고 포트원에 금액을 사전등록함. 재호출도 같은 행으로 사전등록을 다시 태움 */
    fun preparePayment(command: PreparePaymentCommand): PreparePaymentResult

    /**
     * 결제창이 닫힌 뒤의 확정 — 포트원에 진실을 물어 승인이면 주문그룹을 선점 전이하고, 실패면 기록한다.
     * 미완료(미시도·승인 대기)는 전이 없이 상태만 돌려준다. 재호출은 멱등.
     */
    fun completePayment(command: CompletePaymentCommand): CompletePaymentResult
}
