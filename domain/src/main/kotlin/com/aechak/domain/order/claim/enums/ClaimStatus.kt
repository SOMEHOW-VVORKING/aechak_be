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

    /**
     * 셀러 거부(REJECTED)에서 운영자 개입만 열려 있는 이유: 거부는 셀러에게만 종결이고,
     * 운영자가 뒤집을 길이 없으면 구매자와의 분쟁이 끝나지 않는다. 승인으로 되살리는 경로는 열지 않는다 —
     * 거부는 대상 품목을 풀어 줘 그 사이 다른 클레임이 걸릴 수 있고, 되살리는 판단에는 그 확인이 따라야 한다.
     *
     * 철회(WITHDRAWN)와 개입 완료(ADMIN_OVERRIDDEN)에서 아무것도 열리지 않는 이유: 구매자가 스스로
     * 거둔 건과 이미 운영자가 판정한 건에는 뒤집을 분쟁이 없다.
     */
    fun canTransitionTo(next: ClaimStatus): Boolean =
        next in
            when (this) {
                CLAIM_REQUESTED -> setOf(APPROVED, REJECTED, COLLECTING, WITHDRAWN, ADMIN_OVERRIDDEN)
                COLLECTING -> setOf(APPROVED, REJECTED, ADMIN_OVERRIDDEN)
                APPROVED -> setOf(REFUND_FAILED, ADMIN_OVERRIDDEN)
                REFUND_FAILED -> setOf(APPROVED, ADMIN_OVERRIDDEN)
                REJECTED -> setOf(ADMIN_OVERRIDDEN)
                WITHDRAWN, ADMIN_OVERRIDDEN -> emptySet()
            }

    /** 대상 주문품목을 아직 이 클레임이 붙잡고 있는 상태. 거부·철회만 품목을 풀어준다. */
    fun holdsOrderItem(): Boolean = this !in RELEASED

    companion object {
        /** 같은 주문품목에 새 클레임을 허용하는 상태 — 중복 클레임 차단 질의가 쓴다. */
        val RELEASED: Set<ClaimStatus> = setOf(REJECTED, WITHDRAWN)

        val HOLDING: Set<ClaimStatus> = entries.filter { it.holdsOrderItem() }.toSet()

        /**
         * 운영자가 개입할 수 있는 상태. 전이표에서 뽑는다 — 목록 질의와 애그리거트 판정이 각자 적으면
         * 화면이 "개입 가능"으로 보여 준 건을 실행 경로가 거절하는 일이 생긴다.
         * 환불 축(RefundStatus)은 상태로 가릴 수 없어 쓰는 쪽이 함께 본다.
         */
        val ADMIN_OVERRIDABLE: Set<ClaimStatus> = entries.filter { it.canTransitionTo(ADMIN_OVERRIDDEN) }.toSet()
    }
}
