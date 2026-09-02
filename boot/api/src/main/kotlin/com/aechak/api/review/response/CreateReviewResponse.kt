package com.aechak.api.review.response

import com.aechak.application.review.usecase.result.CreateReviewResult
import java.time.LocalDateTime

data class CreateReviewResponse(
    val reviewId: Long,
    val productId: Long,
    val rating: Int,
    val content: String,
    val optionName: String,
    val createdAt: LocalDateTime,
    val images: List<ReviewImageResponse>,
) {
    companion object {
        fun from(result: CreateReviewResult): CreateReviewResponse =
            CreateReviewResponse(
                reviewId = result.reviewId,
                productId = result.productId,
                rating = result.rating,
                content = result.content,
                optionName = result.optionName,
                createdAt = result.createdAt,
                images = result.images.map(ReviewImageResponse::from),
            )
    }
}
