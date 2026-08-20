package com.aechak.domain.payment.error

import com.aechak.common.error.ErrorCode

enum class PaymentErrorCode(
    override val code: Int,
    override val message: String,
    override val status: Int,
) : ErrorCode {
    PAYMENT_NOT_FOUND(60000, "결제 내역을 찾을 수 없습니다.", 404),
    PAYMENT_GATEWAY_ERROR(60005, "결제 게이트웨이 호출에 실패했습니다.", 502),
    PAYMENT_ALREADY_PAID(60006, "이미 결제가 완료된 건입니다.", 409),
}
