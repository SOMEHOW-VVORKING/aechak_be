package com.aechak.message.review

import com.aechak.message.GuaranteedMessage
import java.time.Instant

data class ReviewDeletedMessage(
    val reviewId: Long,
    val productId: Long,
    override val occurredAt: Instant = Instant.now(),
) : GuaranteedMessage {
    override val aggregateType: String get() = "review"
    override val orderingKey: String get() = productId.toString()
    override val eventId: String get() = "review-$reviewId:deleted"
}
