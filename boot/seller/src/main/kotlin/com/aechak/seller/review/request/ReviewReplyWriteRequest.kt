package com.aechak.seller.review.request

import com.aechak.application.review.seller.usecase.command.WriteReviewReplyCommand
import com.aechak.domain.review.reply.ReviewReply
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class ReviewReplyWriteRequest(
    @field:NotBlank(message = "답글 내용은 필수입니다.")
    @field:Size(max = ReviewReply.CONTENT_MAX, message = "답글은 최대 {max}자입니다.")
    val content: String,
) {
    fun toCommand(
        sellerId: Long,
        reviewId: Long,
    ): WriteReviewReplyCommand =
        WriteReviewReplyCommand(
            sellerId = sellerId,
            reviewId = reviewId,
            content = content,
        )
}
