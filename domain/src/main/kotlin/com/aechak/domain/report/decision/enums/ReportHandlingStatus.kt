package com.aechak.domain.report.decision.enums

/** 운영자가 보는 신고의 처리 상태. 저장하지 않고 report_decisions 행에서 파생한다. */
enum class ReportHandlingStatus {
    /** 결정 기록이 없다 */
    RECEIVED,

    /** 제재 없이 종결 */
    DISMISSED,

    /** 비공개 전환 또는 계정 제재로 종결 */
    SANCTIONED,
}
