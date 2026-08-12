package com.aechak.application.review.service

import com.aechak.common.error.BusinessException
import com.aechak.domain.review.error.ReviewErrorCode
import com.aechak.domain.review.review.repository.ReviewRepository
import org.springframework.stereotype.Service

@Service
class ReviewCommandService(
    private val reviewRepository: ReviewRepository,
) {
    fun delete(
        userId: Long,
        reviewId: Long,
    ) {
        val review = reviewRepository.findById(reviewId) ?: throw BusinessException(ReviewErrorCode.REVIEW_NOT_FOUND)
        if (review.authorUserId != userId) {
            throw BusinessException(ReviewErrorCode.REVIEW_ACCESS_DENIED)
        }
        reviewRepository.markDeletedIfNotDeleted(reviewId, userId)
    }
}
