package com.aechak.application.review.usecase

import com.aechak.application.review.usecase.command.CreateReviewCommand
import com.aechak.application.review.usecase.result.CreateReviewResult

interface ReviewCommandUseCase {
    fun createReview(command: CreateReviewCommand): CreateReviewResult

    fun deleteReview(
        userId: Long,
        reviewId: Long,
    )
}
