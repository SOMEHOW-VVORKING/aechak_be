package com.aechak.application.payment.usecase.command

/** 로그인 주체 없이 결제 식별자만으로 확정 본체에 들어가는 입구(대사·만료 배치). 신원은 서버 내부 호출이 보증한다 */
data class CompletePaymentByGatewayCommand(
    val paymentId: String,
)
