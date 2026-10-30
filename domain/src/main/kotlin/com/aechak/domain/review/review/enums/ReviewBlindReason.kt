package com.aechak.domain.review.review.enums

/**
 * 블라인드·삭제 사유 코드. PROFANITY만 자동 판정이 쓰고 나머지는 운영자가 고른다.
 *
 * [label]은 감사 로그의 사유 칸처럼 운영자가 읽는 자리에 쓴다 — 열거 이름을 그대로 실으면
 * 조치 이력을 읽을 때마다 코드를 해독해야 한다. API 응답은 열거 이름을 그대로 내보내므로
 * (허용값이 생성 문서에 실려야 한다) 이 문구가 계약을 바꾸지는 않는다.
 */
enum class ReviewBlindReason(
    val label: String,
) {
    /** 금칙어 판정 */
    PROFANITY("금칙어 포함"),
    ABUSE("욕설·비방"),
    EXTERNAL_TRADE("외부 거래 유도"),
    AD_SPAM("광고·도배"),
    PRIVACY("개인정보 노출"),
    IRRELEVANT("상품과 무관한 내용"),
    OTHER("기타"),
}
