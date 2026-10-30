package com.aechak.domain.report.decision.enums

/** 운영자의 신고 처리 결정. 화면의 라디오 3종과 1:1 이다. */
enum class ReportDecisionType {
    /** 제재 없음 — 기록만 남기고 대상은 그대로 노출된다 */
    DISMISS,

    /** 비공개 전환 — 대상만 비노출 */
    HIDE_TARGET,

    /** 계정 제재 — 대상 비노출 + 소유자 계정에 이용 제한 */
    SANCTION_ACCOUNT,
    ;

    fun hidesTarget(): Boolean = this == HIDE_TARGET || this == SANCTION_ACCOUNT

    fun sanctions(): Boolean = this != DISMISS
}
