package com.aechak.domain.report.decision.enums

/**
 * 신고 처리 사유 고정 어휘. 화면의 처리 사유 6종과 1:1 이다.
 *
 * [label]은 감사 로그의 사유 칸처럼 운영자가 읽는 자리에 쓴다 — 열거 이름을 그대로 실으면
 * 조치 이력을 읽을 때마다 코드를 해독해야 한다. API 응답은 열거 이름을 그대로 내보내므로
 * (허용값이 생성 문서에 실려야 한다) 이 문구가 계약을 바꾸지는 않는다.
 */
enum class ReportDecisionReason(
    val label: String,
) {
    FALSE_AD("허위·과장 광고"),
    ABUSE("욕설·비방"),
    EXTERNAL_TRADE("외부 거래 유도"),
    ILLEGAL("불법·미취급 상품"),
    FALSE_REPORT("허위 신고 반복"),
    NOT_CONFIRMED("신고 내용 확인 불가"),
    ;

    /** 대상에게 위반이 없었다는 판정인지 — 종결(제재 없음) 전용 사유. */
    fun indicatesNoViolationByTarget(): Boolean = this == NOT_CONFIRMED || this == FALSE_REPORT
}
