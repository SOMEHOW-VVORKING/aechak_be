package com.aechak.domain.report.decision.enums

/** 신고 대상의 종류. 신고 행이 어느 표(product/review/user_reports)에서 왔는지를 가린다. */
enum class ReportTargetType {
    PRODUCT,
    REVIEW,
    USER,
    ;

    /**
     * 신고 처리가 비노출을 걸 수 있는 종류인지.
     *
     * 리뷰만 참이다. 사용자는 숨길 콘텐츠가 아니고, 상품은 노출을 되돌리지 않고 유지할 수단이
     * 아직 없다 — 판매 상태는 셀러가 되돌릴 수 있고 검수 상태는 검수 큐의 관리자 승인이
     * 신고 결정을 보지 않고 되돌린다.
     */
    fun hasHidableTarget(): Boolean = this == REVIEW
}
