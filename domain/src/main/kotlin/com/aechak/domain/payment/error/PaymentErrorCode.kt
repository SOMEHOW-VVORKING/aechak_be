package com.aechak.domain.payment.error

import com.aechak.common.error.ErrorCode

enum class PaymentErrorCode(
    override val code: Int,
    override val message: String,
    override val status: Int,
) : ErrorCode {
    PAYMENT_NOT_FOUND(60001, "결제 내역을 찾을 수 없습니다.", 404),
}
