package com.aechak.application.review.usecase.result

import com.aechak.domain.review.review.Review
import java.time.LocalDateTime

/** 리뷰 작성 결과 */
data class CreateReviewResult(
    val reviewId: Long,
    val productId: Long,
    val rating: Int,
    val content: String,
    val optionName: String,
    val createdAt: LocalDateTime,
    val images: List<ReviewImageItemResult>,
) {
    companion object {
        fun from(
            review: Review,
            images: List<ReviewImageItemResult>,
        ): CreateReviewResult =
            CreateReviewResult(
                reviewId = review.id,
                productId = review.productId,
                rating = review.rating,
                content = review.content,
                optionName = review.optionNameSnapshot,
                createdAt = review.createdAt,
                images = images,
            )
    }
}
