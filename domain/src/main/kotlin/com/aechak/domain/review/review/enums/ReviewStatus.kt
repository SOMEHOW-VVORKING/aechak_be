package com.aechak.domain.review.review.enums

enum class ReviewStatus {
    PUBLIC,

    /** 마스킹(display_content 대체 노출) */
    MASKED,
    BLOCKED,
    HIDDEN,
    DELETED,
    ;

    fun isVisible(): Boolean = this == PUBLIC || this == MASKED
}
