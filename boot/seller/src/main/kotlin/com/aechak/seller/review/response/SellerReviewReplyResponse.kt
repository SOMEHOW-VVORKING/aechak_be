package com.aechak.seller.review.response

import com.aechak.application.review.usecase.result.ReviewReplyResult
import java.time.LocalDateTime

data class SellerReviewReplyResponse(
    val replyId: Long,
    val reviewId: Long,
    val content: String,
    val repliedAt: LocalDateTime,
    val updatedAt: LocalDateTime,
) {
    companion object {
        fun from(result: ReviewReplyResult): SellerReviewReplyResponse =
            SellerReviewReplyResponse(
                replyId = result.replyId,
                reviewId = result.reviewId,
                content = result.content,
                repliedAt = result.repliedAt,
                updatedAt = result.updatedAt,
            )
    }
}
