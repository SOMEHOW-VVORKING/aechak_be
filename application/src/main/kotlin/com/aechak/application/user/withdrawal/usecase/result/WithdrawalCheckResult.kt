package com.aechak.application.user.withdrawal.usecase.result

/** 탈퇴 가능 여부와 차단 사유 */
data class WithdrawalCheckResult(
    val blockers: List<WithdrawalBlocker>,
) {
    val withdrawable: Boolean get() = blockers.isEmpty()
}

data class WithdrawalBlocker(
    val reason: WithdrawalBlockReason,
    val count: Int,
)

/** 탈퇴 차단 사유. 선언 순서가 응답의 blockers 순서다 */
enum class WithdrawalBlockReason {
    SELLER_ACCOUNT, // 퇴점하지 않은 셀러 계정
    PAYMENT_IN_PROGRESS, // 만료 전 결제대기 주문그룹
}
