package com.aechak.domain.order.claim.enums

enum class ClaimStatus {
    CLAIM_REQUESTED,
    COLLECTING,
    APPROVED,
    REJECTED,
    ADMIN_OVERRIDDEN,
    REFUND_FAILED,
    WITHDRAWN,
    ;

    fun canTransitionTo(next: ClaimStatus): Boolean =
        next in
            when (this) {
                CLAIM_REQUESTED -> setOf(APPROVED, REJECTED, COLLECTING, WITHDRAWN, ADMIN_OVERRIDDEN)
                COLLECTING -> setOf(APPROVED, REJECTED, ADMIN_OVERRIDDEN)
                APPROVED -> setOf(REFUND_FAILED, ADMIN_OVERRIDDEN)
                REFUND_FAILED -> setOf(APPROVED, ADMIN_OVERRIDDEN)
                REJECTED, WITHDRAWN, ADMIN_OVERRIDDEN -> emptySet()
            }

    /** 대상 주문품목을 아직 이 클레임이 붙잡고 있는 상태. 거부·철회만 품목을 풀어준다. */
    fun holdsOrderItem(): Boolean = this !in RELEASED

    companion object {
        /** 같은 주문품목에 새 클레임을 허용하는 상태 — 중복 클레임 차단 질의가 쓴다. */
        val RELEASED: Set<ClaimStatus> = setOf(REJECTED, WITHDRAWN)

        val HOLDING: Set<ClaimStatus> = entries.filter { it.holdsOrderItem() }.toSet()
    }
}
