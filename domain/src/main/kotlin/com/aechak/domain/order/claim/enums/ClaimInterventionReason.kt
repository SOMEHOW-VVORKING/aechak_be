package com.aechak.domain.order.claim.enums

/**
 * 운영자 개입 사유 고정 어휘. 자유 문자열로 두면 분쟁 기록을 나중에 집계할 수 없어
 * 클레임 요청 사유(ClaimReasonCode)와 같은 모양으로 코드 + 상세를 받는다.
 * label은 운영 화면 선택지에 그대로 쓰는 문구다.
 *
 * 판정별로 고를 수 있는 사유는 [selectableFor]가 가른다 — "셀러 귀책인데 구매자 악성 요청"처럼
 * 서로 모순인 조합이 기록에 남으면 그 기록으로는 아무것도 판단할 수 없다.
 */
enum class ClaimInterventionReason(
    val label: String,
) {
    SELLER_UNRESPONSIVE("셀러가 기한 내 응답하지 않았어요"),
    SELLER_DECISION_UNFAIR("셀러 처리가 운영 정책에 맞지 않아요"),
    EVIDENCE_SUPPORTS_BUYER("구매자 주장에 근거가 있어요"),
    EVIDENCE_SUPPORTS_SELLER("셀러 주장에 근거가 있어요"),
    BUYER_CHANGE_OF_MIND("구매자 단순 변심으로 확인됐어요"),
    BUYER_ABUSE("구매자의 반복·악성 요청이에요"),
    POLICY_VIOLATION("운영 정책 위반이 확인됐어요"),
    OTHER("기타"),
    ;

    /** 기타는 코드만으로는 판단 근거가 되지 않아 상세 입력을 요구한다. */
    fun requiresDetail(): Boolean = this == OTHER

    fun isSelectableFor(decision: ClaimFaultDecision): Boolean = this in selectableFor(decision)

    companion object {
        /** 판정별로 고를 수 있는 사유. 나열 순서가 화면 선택지 순서다. */
        fun selectableFor(decision: ClaimFaultDecision): List<ClaimInterventionReason> =
            when (decision) {
                ClaimFaultDecision.SELLER_FAULT -> {
                    listOf(SELLER_UNRESPONSIVE, SELLER_DECISION_UNFAIR, EVIDENCE_SUPPORTS_BUYER, OTHER)
                }

                ClaimFaultDecision.BUYER_FAULT -> {
                    listOf(EVIDENCE_SUPPORTS_SELLER, BUYER_CHANGE_OF_MIND, OTHER)
                }

                ClaimFaultDecision.CLAIM_DISMISSED -> {
                    listOf(EVIDENCE_SUPPORTS_SELLER, BUYER_ABUSE, POLICY_VIOLATION, OTHER)
                }
            }
    }
}
