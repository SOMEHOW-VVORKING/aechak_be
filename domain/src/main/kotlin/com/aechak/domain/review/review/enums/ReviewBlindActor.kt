package com.aechak.domain.review.review.enums

/** 블라인드·삭제를 일으킨 주체. 자동 탐지와 운영자 수동 처리를 구별한다. */
enum class ReviewBlindActor {
    /** 작성 시점 금칙어 판정의 자동 처리 */
    AI,

    /** 운영자 수동 처리 */
    ADMIN,
}
