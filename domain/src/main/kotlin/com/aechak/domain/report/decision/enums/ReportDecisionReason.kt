package com.aechak.domain.report.decision.enums

/** 신고 처리 사유 고정 어휘. 화면의 처리 사유 6종과 1:1 이다. */
enum class ReportDecisionReason {
    /** 허위·과장 광고 */
    FALSE_AD,

    /** 욕설·비방 */
    ABUSE,

    /** 외부 거래 유도 */
    EXTERNAL_TRADE,

    /** 불법·미취급 상품 */
    ILLEGAL,

    /** 허위 신고 반복 */
    FALSE_REPORT,

    /** 신고 내용 확인 불가 */
    NOT_CONFIRMED,
    ;

    /** 대상에게 위반이 없었다는 판정인지 — 종결(제재 없음) 전용 사유. */
    fun indicatesNoViolationByTarget(): Boolean = this == NOT_CONFIRMED || this == FALSE_REPORT
}
