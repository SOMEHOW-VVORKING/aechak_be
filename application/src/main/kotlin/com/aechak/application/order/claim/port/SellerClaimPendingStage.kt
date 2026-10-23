package com.aechak.application.order.claim.port

/**
 * 셀러가 아직 해야 할 일이 남은 클레임 단계. 대시보드 처리 대기 집계와 클레임 목록 필터가 이 축 하나를 공유한다 —
 * 판정이 두 벌이면 같은 라벨에 다른 숫자가 뜨고, 그 자체가 결함 신고가 된다.
 *
 * 상태 하나로 갈리지 않는 자리가 둘이다.
 * 회수 중(COLLECTING)에는 송장 등록과 완료 처리라는 서로 다른 할 일이 들어 있어 **회수 시작 시각**으로 가른다.
 * 승인됨(APPROVED)에는 반품의 환불 대기(셀러가 할 일 없음)와 교환의 재발송 대기가 섞여 있어
 * **유형과 종결 시각**을 함께 본다.
 */
enum class SellerClaimPendingStage {
    /** 승인·거부 판단 대기 — 요청 상태. */
    DECISION_PENDING,

    /** 회수 송장 등록 대기 — 승인됐지만 회수를 아직 시작하지 않은 반품·교환. */
    COLLECTION_INVOICE_PENDING,

    /** 회수 완료 처리 대기 — 회수 송장을 등록했고 물건 도착을 아직 확정하지 않은 건. */
    COLLECTION_COMPLETION_PENDING,

    /** 재발송 대기 — 회수가 끝난 교환. 반품은 환불이 뒤를 이어 셀러가 할 일이 없다. */
    RESHIP_PENDING,
}
