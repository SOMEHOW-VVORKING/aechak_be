package com.aechak.application.review.moderation.service

/** 리뷰 내용 판정 결과 */
sealed interface ReviewModerationDecision {
    data object Keep : ReviewModerationDecision

    data class Mask(
        val displayContent: String,
    ) : ReviewModerationDecision

    data object Block : ReviewModerationDecision
}
