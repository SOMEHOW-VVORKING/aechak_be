package com.aechak.application.review.summary.port.view

import com.aechak.domain.review.review.enums.ReviewStatus
import java.time.LocalDateTime

data class SummaryReviewContentView(
    val reviewId: Long,
    val rating: Int,
    val reviewStatus: ReviewStatus,
    val content: String,
    val displayContent: String?,
    val optionName: String,
    val hasPhoto: Boolean,
    val createdAt: LocalDateTime,
)
