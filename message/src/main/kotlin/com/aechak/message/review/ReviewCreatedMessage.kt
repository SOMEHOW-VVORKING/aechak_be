package com.aechak.message.review

import com.aechak.message.GuaranteedMessage
import java.time.Instant

data class ReviewCreatedMessage(
    val reviewId: Long,
    val productId: Long,
    val buyerUserId: Long,
    val hasPhoto: Boolean,
    val reviewStatus: ReviewModerationStatus,
    override val occurredAt: Instant = Instant.now(),
) : GuaranteedMessage {
    override val aggregateType: String get() = "review"
    override val aggregateId: String get() = productId.toString()
    override val eventId: String get() = "review-$reviewId:created"
}

enum class ReviewModerationStatus {
    PUBLIC,
    MASKED,
    BLOCKED,
}
