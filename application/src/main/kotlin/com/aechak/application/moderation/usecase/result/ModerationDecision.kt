package com.aechak.application.moderation.usecase.result

/** 글 판정 결과 */
sealed interface ModerationDecision {
    data object Keep : ModerationDecision

    data class Mask(
        val displayContent: String,
    ) : ModerationDecision

    data object Block : ModerationDecision
}
