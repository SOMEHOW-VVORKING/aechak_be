package com.aechak.domain.settlement.error

import com.aechak.common.error.ErrorCode

enum class SettlementErrorCode(
    override val code: Int,
    override val message: String,
    override val status: Int,
) : ErrorCode {
    INVALID_SETTLEMENT_AMOUNT(70000, "정산 금액이 올바르지 않습니다.", 400),
    SETTLEMENT_NOT_PENDING(70001, "정산대기 상태에서만 처리할 수 있습니다.", 409),
    SETTLEMENT_NOT_FOUND(70002, "정산 내역을 찾을 수 없습니다.", 404),
    SETTLEMENT_ACCESS_DENIED(70003, "해당 정산 내역에 접근할 수 없습니다.", 403),

    // 요율 정책 행이 없으면 원장을 만들 수 없다. 운영 설정 누락이라 사용자가 고칠 수 없어 500이다.
    SETTLEMENT_FEE_POLICY_NOT_FOUND(70005, "정산 수수료율 정책을 찾을 수 없습니다.", 500),
    INVALID_SETTLEMENT_STATUS_TRANSITION(70006, "정산 상태를 변경할 수 없습니다.", 409),
    INVALID_SETTLEMENT_FEE_RATE(70007, "정산 수수료율이 올바르지 않습니다.", 400),

    // 집행 배치가 지급 묶음 연결을 선점하지 못했을 때. 지급 건 생성 트랜잭션을 되돌리는 신호다.
    // 검증 대상이 정산 원장이라 Settlement 대역에 둔다.
    PAYOUT_TARGET_TAKEN(70008, "다른 실행이 지급 대상 정산을 먼저 선점했습니다.", 409),
    INVALID_PAYOUT_STATUS_TRANSITION(70101, "지급 상태를 변경할 수 없습니다.", 409),
    INVALID_SETTLEMENT_BATCH_STATUS_TRANSITION(70201, "정산 집행 배치 상태를 변경할 수 없습니다.", 409),
}
