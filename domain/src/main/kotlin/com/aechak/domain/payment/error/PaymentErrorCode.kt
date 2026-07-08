package com.aechak.domain.payment.error

import com.aechak.common.error.ErrorCode

/**
 * payment 도메인 에러 코드 — 대역 60000~60999.
 * 새 코드는 이 파일에만 추가한다. PG 연동 실패 계열은 status 502를 쓴다.
 */
enum class PaymentErrorCode(
    override val code: Int,
    override val message: String,
    override val status: Int,
) : ErrorCode {

    PAYMENT_NOT_FOUND(60001, "결제 내역을 찾을 수 없습니다.", 404),
    // TODO: 도메인 기능 구현하며 추가
}
