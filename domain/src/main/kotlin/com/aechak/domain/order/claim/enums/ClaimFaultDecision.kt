package com.aechak.domain.order.claim.enums

/**
 * 운영자 귀책 판정. 셀러 처리를 덮을 때 분쟁의 책임을 어디에 두는지 고른다 — 세 값이 화면의 판정 버튼 셋과 1:1이다.
 *
 * [claimAccepted]는 클레임 자체가 성립했다고 보는지,
 * [defaultReturnCostBearer]는 판정만으로 정해지는 반품비 부담 주체다(운영자가 명시하면 그 값이 이긴다).
 */
enum class ClaimFaultDecision(
    val claimAccepted: Boolean,
    val defaultReturnCostBearer: ReturnCostBearer?,
) {
    SELLER_FAULT(claimAccepted = true, defaultReturnCostBearer = ReturnCostBearer.SELLER),
    BUYER_FAULT(claimAccepted = true, defaultReturnCostBearer = ReturnCostBearer.BUYER),

    /** 클레임 불가 — 어느 쪽 귀책도 아니고 요청이 성립하지 않는다. 반품 발송이 없어 부담 주체도 없다. */
    CLAIM_DISMISSED(claimAccepted = false, defaultReturnCostBearer = null),
}
