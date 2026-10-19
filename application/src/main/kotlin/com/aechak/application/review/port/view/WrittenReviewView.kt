package com.aechak.application.review.port.view

import com.aechak.domain.review.review.enums.ReviewStatus
import java.time.LocalDateTime

data class WrittenReviewView(
    val reviewId: Long,
    val rating: Int,
    val content: String,
    val displayContent: String?,
    val reviewStatus: ReviewStatus,
    val optionNameSnapshot: String,
    val productPublicId: String,
    val productNameSnapshot: String,
    val thumbnailKeySnapshot: String,
    val createdAt: LocalDateTime,
)

data class UnreviewedOrderItemView(
    val orderItemId: Long,
    val productPublicId: String,
    val productNameSnapshot: String,
    val thumbnailKeySnapshot: String,
    val optionNameSnapshot: String,
    val purchaseConfirmedAt: LocalDateTime,
)
