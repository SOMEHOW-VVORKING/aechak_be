package com.aechak.application.review.port.view

import com.aechak.domain.review.review.enums.ReviewStatus
import java.time.LocalDateTime

data class ReviewView(
    val id: Long,
    val rating: Int,
    val content: String,
    val displayContent: String?,
    val reviewStatus: ReviewStatus,
    val optionNameSnapshot: String,
    val authorUserId: Long,
    val createdAt: LocalDateTime,
)
