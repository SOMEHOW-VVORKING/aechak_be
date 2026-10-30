package com.aechak.domain.report.decision.enums

/** 신고 대상의 종류. 신고 행이 어느 표(product/review/user_reports)에서 왔는지를 가린다. */
enum class ReportTargetType {
    PRODUCT,
    REVIEW,
    USER,
    ;

    /** 숨길 콘텐츠가 있는 종류인지 — 사용자는 비노출 대상이 없다. */
    fun hasHidableTarget(): Boolean = this != USER
}
