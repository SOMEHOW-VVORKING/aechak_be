package com.aechak.domain.settlement.settlement.enums

enum class SettlementStatus {
    PENDING,

    /** 진행 중 클레임이 있어 지급 대상에서 뺀 상태. 해제되면 PENDING으로 돌아간다. */
    ON_HOLD,

    PAID,
    CANCELLED,
    ;

    fun canTransitionTo(next: SettlementStatus): Boolean =
        next in
            when (this) {
                PENDING -> setOf(ON_HOLD, PAID, CANCELLED)
                ON_HOLD -> setOf(PENDING, CANCELLED)
                PAID, CANCELLED -> emptySet()
            }
}
