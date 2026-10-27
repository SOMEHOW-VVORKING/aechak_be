package com.aechak.application.order.claim.port

import com.aechak.domain.order.claim.enums.ClaimStatus

/**
 * 셀러가 아직 해야 할 일이 남은 클레임 단계. 대시보드 처리 대기 집계와 클레임 목록 필터가 이 축 하나를 공유한다 —
 * 판정이 두 벌이면 같은 라벨에 다른 숫자가 뜨고, 그 자체가 결함 신고가 된다.
 *
 * 상태 하나로 갈리지 않는 자리가 둘이다.
 * 회수 중(COLLECTING)에는 송장 등록과 완료 처리라는 서로 다른 할 일이 들어 있어 **회수 시작 시각**으로 가른다.
 * 승인됨(APPROVED)에는 반품의 환불 대기(셀러가 할 일 없음)와 교환의 재발송 대기가 섞여 있어
 * **유형과 종결 시각**을 함께 본다.
 *
 * [statuses]는 단계가 놓일 수 있는 상태다. 집계 쿼리가 [PENDING_STATUSES]로 읽을 행을 먼저 좁히므로
 * 상태 축은 여기 한 곳에만 적는다 — 단계를 더하면서 좁히는 조건을 못 고치면 그 단계가 조용히 0이 된다.
 */
enum class SellerClaimPendingStage(
    val statuses: Set<ClaimStatus>,
) {
    /** 승인·거부 판단 대기 — 요청 상태. */
    DECISION_PENDING(setOf(ClaimStatus.CLAIM_REQUESTED)),

    /** 회수 송장 등록 대기 — 승인됐지만 회수를 아직 시작하지 않은 반품·교환. */
    COLLECTION_INVOICE_PENDING(setOf(ClaimStatus.COLLECTING)),

    /** 회수 완료 처리 대기 — 회수 송장을 등록했고 물건 도착을 아직 확정하지 않은 건. */
    COLLECTION_COMPLETION_PENDING(setOf(ClaimStatus.COLLECTING)),

    /** 재발송 대기 — 회수가 끝난 교환. 반품은 환불이 뒤를 이어 셀러가 할 일이 없다. */
    RESHIP_PENDING(setOf(ClaimStatus.APPROVED)),
    ;

    companion object {
        /**
         * 어느 단계에도 들어갈 수 없는 상태를 집계 입력에서 미리 뺀다. 거부·철회·개입·환불 실패는
         * 셀러가 할 일이 없는데도 이력으로 끝없이 쌓여, 조건을 조건부 합계 안에만 두면 첫 화면 질의가
         * 그 셀러의 클레임 전수를 읽는다. 단계별 조건이 모두 이 집합 안에 있어 결과는 바뀌지 않는다.
         */
        val PENDING_STATUSES: Set<ClaimStatus> = entries.flatMapTo(mutableSetOf()) { it.statuses }
    }
}
