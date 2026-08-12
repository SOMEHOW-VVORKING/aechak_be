package com.aechak.application.review.facade

import com.aechak.application.review.service.ReviewCommandService
import com.aechak.application.review.usecase.ReviewCommandUseCase
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ReviewCommandFacade(
    private val reviewCommandService: ReviewCommandService,
) : ReviewCommandUseCase {
    @Transactional
    override fun deleteReview(
        userId: Long,
        reviewId: Long,
    ) {
        reviewCommandService.delete(userId, reviewId)
    }
}
