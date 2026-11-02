package com.aechak.application.report.admin.port

/**
 * 비노출 조치 직전에 리뷰의 현재 노출 여부를 잠근 채 읽는다. 리뷰만 비노출 축을 가진다
 * (`ReportTargetType.hasHidableTarget`).
 *
 * 잠금 없는 재조회로 바꾸지 말 것 — 기본 격리(REPEATABLE READ)에서 재조회는 트랜잭션 시작 시점
 * 스냅숏을 보므로 최신 커밋을 읽는 것은 잠금 조회뿐이다(`AdminReviewService` 와 같은 판단).
 */
interface ReportReviewVisibilityPort {
    /** 리뷰 행을 잠그고 지금 가려져 있는지 읽는다. 행이 없으면 null. */
    fun lockAndReadHidden(reviewId: Long): Boolean?
}
