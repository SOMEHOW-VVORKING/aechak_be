package com.aechak.domain.review.review.enums

/** 블라인드·삭제 사유 코드. PROFANITY만 자동 판정이 쓰고 나머지는 운영자가 고른다. */
enum class ReviewBlindReason {
    /** 금칙어 판정 */
    PROFANITY,
    ABUSE,
    EXTERNAL_TRADE,
    AD_SPAM,
    PRIVACY,
    IRRELEVANT,
    OTHER,
}
