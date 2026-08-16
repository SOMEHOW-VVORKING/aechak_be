package com.aechak.application.review.service

import com.aechak.application.order.usecase.result.ReviewOrderItemResult
import com.aechak.application.review.usecase.command.CreateReviewCommand
import com.aechak.common.error.BusinessException
import com.aechak.domain.review.error.ReviewErrorCode
import com.aechak.domain.review.review.Review
import com.aechak.domain.review.review.ReviewImage
import com.aechak.domain.review.review.repository.DuplicateOrderItemReviewException
import com.aechak.domain.review.review.repository.ReviewRepository
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class ReviewCommandService(
    private val reviewRepository: ReviewRepository,
) {
    fun ensureCanCreateReview(
        command: CreateReviewCommand,
        orderItem: ReviewOrderItemResult,
    ) {
        val confirmedAt = orderItem.purchaseConfirmedAt
        if (!orderItem.isPurchaseConfirmed || confirmedAt == null) {
            throw BusinessException(ReviewErrorCode.REVIEW_NOT_PURCHASE_CONFIRMED)
        }
        if (confirmedAt.plusDays(REVIEW_WINDOW_DAYS).isBefore(LocalDateTime.now())) {
            throw BusinessException(ReviewErrorCode.REVIEW_WINDOW_EXPIRED)
        }
        if (!orderItem.isItemReviewable) {
            throw BusinessException(ReviewErrorCode.REVIEW_ITEM_NOT_REVIEWABLE)
        }
        if (reviewRepository.existsByOrderItemId(command.orderItemId)) {
            throw BusinessException(ReviewErrorCode.REVIEW_ALREADY_WRITTEN)
        }
    }

    fun create(
        command: CreateReviewCommand,
        orderItem: ReviewOrderItemResult,
        images: List<ReviewImage>,
    ): Review {
        val review = command.toEntity(orderItem, images)
        return try {
            reviewRepository.save(review)
        } catch (e: DuplicateOrderItemReviewException) {
            throw BusinessException(ReviewErrorCode.REVIEW_ALREADY_WRITTEN, e)
        }
    }

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

    companion object {
        const val REVIEW_WINDOW_DAYS = 30L
    }
}
