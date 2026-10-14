package com.aechak.application.payment.usecase

import com.aechak.application.payment.usecase.command.PreparePaymentCommand
import com.aechak.application.payment.usecase.result.PreparePaymentResult

interface PaymentUseCase {
    /** 결제 행을 만들고 포트원에 금액을 사전등록함. 재호출도 같은 행으로 사전등록을 다시 태움 */
    fun preparePayment(command: PreparePaymentCommand): PreparePaymentResult
}
