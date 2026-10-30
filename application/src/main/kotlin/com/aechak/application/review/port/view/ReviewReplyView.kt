package com.aechak.application.review.port.view

import java.time.LocalDateTime

data class ReviewReplyView(
    val replyId: Long,
    val reviewId: Long,
    val content: String,
    val repliedAt: LocalDateTime,
    val updatedAt: LocalDateTime,
)
