package com.aechak.domain.settlement.error

import com.aechak.common.error.ErrorCode

/**
 * [잠정값 경고] 대역 미확정 — 기존 대역(셀러 10000~/주문 50000~/결제 60000~/배송 70000~/공통 90000~)과
 * 겹치지 않게 100000~을 잠정 사용한다. 확정 시 코드 값만 교체.
 */
enum class SettlementErrorCode(
    override val code: Int,
    override val message: String,
    override val status: Int,
) : ErrorCode {
    INVALID_SETTLEMENT_AMOUNT(100001, "정산 금액이 올바르지 않습니다.", 400),
    SETTLEMENT_NOT_PENDING(100002, "정산대기 상태에서만 처리할 수 있습니다.", 409),
}
