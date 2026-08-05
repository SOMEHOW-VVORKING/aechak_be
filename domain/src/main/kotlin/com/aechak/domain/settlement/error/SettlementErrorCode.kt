package com.aechak.domain.settlement.error

import com.aechak.common.error.ErrorCode

enum class SettlementErrorCode(
    override val code: Int,
    override val message: String,
    override val status: Int,
) : ErrorCode {
    INVALID_SETTLEMENT_AMOUNT(70000, "정산 금액이 올바르지 않습니다.", 400),
    SETTLEMENT_NOT_PENDING(70001, "정산대기 상태에서만 처리할 수 있습니다.", 409),
}
