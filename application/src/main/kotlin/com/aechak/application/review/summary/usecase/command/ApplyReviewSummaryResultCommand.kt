package com.aechak.application.review.summary.usecase.command

import com.aechak.message.review.ReviewSummaryMessage

data class ApplyReviewSummaryResultCommand(
    val message: ReviewSummaryMessage,
)
