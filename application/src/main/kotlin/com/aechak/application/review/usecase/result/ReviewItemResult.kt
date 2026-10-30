package com.aechak.application.review.usecase.result

import com.aechak.application.review.port.view.ReviewView
import com.aechak.domain.review.review.Review
import java.time.LocalDateTime

data class ReviewItemResult(
    val reviewId: Long,
    val rating: Int,
    val content: String,
    val optionName: String,
    val authorNickname: String,
    val authorProfileImageUrl: String?,
    val createdAt: LocalDateTime,
    val images: List<ReviewImageItemResult>,
    val isMine: Boolean,
    val sellerReply: ReviewReplyResult?,
) {
    companion object {
        fun from(
            view: ReviewView,
            images: List<ReviewImageItemResult>,
            authorNickname: String,
            authorProfileImageUrl: String?,
            isMine: Boolean,
            sellerReply: ReviewReplyResult?,
        ): ReviewItemResult =
            ReviewItemResult(
                reviewId = view.id,
                rating = view.rating,
                content = Review.visibleContent(view.reviewStatus, view.content, view.displayContent),
                optionName = view.optionNameSnapshot,
                authorNickname = authorNickname,
                authorProfileImageUrl = authorProfileImageUrl,
                createdAt = view.createdAt,
                images = images,
                isMine = isMine,
                sellerReply = sellerReply,
            )
    }
}

data class ReviewImageItemResult(
    val imageUrl: String,
    val sortOrder: Int,
)
