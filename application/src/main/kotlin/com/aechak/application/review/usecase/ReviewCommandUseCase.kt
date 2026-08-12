package com.aechak.application.review.usecase

interface ReviewCommandUseCase {
    fun deleteReview(
        userId: Long,
        reviewId: Long,
    )
}
