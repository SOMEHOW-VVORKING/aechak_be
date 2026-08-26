package com.aechak.application.review.service

import com.aechak.application.order.usecase.result.ReviewOrderItemResult
import com.aechak.application.review.moderation.service.ReviewModerationDecision
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
        if (!Review.isWithinWriteWindow(confirmedAt, LocalDateTime.now())) {
            throw BusinessException(ReviewErrorCode.REVIEW_WINDOW_EXPIRED)
        }
        if (!orderItem.isItemReviewable) {
            throw BusinessException(ReviewErrorCode.REVIEW_ITEM_NOT_REVIEWABLE)
        }
        if (reviewRepository.existsByOrderItemId(command.orderItemId)) {
            throw BusinessException(ReviewErrorCode.REVIEW_ALREADY_WRITTEN)
        }
    }

    fun resolveImageKeys(command: CreateReviewCommand): List<String> {
        val imageKeys = command.imageKeys.distinct()
        if (imageKeys.size > Review.MAX_IMAGES) {
            throw BusinessException(ReviewErrorCode.REVIEW_TOO_MANY_IMAGES)
        }
        return imageKeys
    }

    fun create(
        command: CreateReviewCommand,
        orderItem: ReviewOrderItemResult,
        images: List<ReviewImage>,
        moderationDecision: ReviewModerationDecision,
    ): Review {
        val review = command.toEntity(orderItem, images)
        when (moderationDecision) {
            is ReviewModerationDecision.Keep -> Unit
            is ReviewModerationDecision.Mask -> review.mask(moderationDecision.displayContent)
            is ReviewModerationDecision.Block -> review.block()
        }
        return try {
            reviewRepository.save(review)
        } catch (e: DuplicateOrderItemReviewException) {
            throw BusinessException(ReviewErrorCode.REVIEW_ALREADY_WRITTEN, e)
        }
    }

    /** 삭제 상태 변경 시 재집계 대상 productId 반환 */
    fun delete(
        userId: Long,
        reviewId: Long,
    ): Long? {
        val review = reviewRepository.findById(reviewId) ?: throw BusinessException(ReviewErrorCode.REVIEW_NOT_FOUND)
        if (review.authorUserId != userId) {
            throw BusinessException(ReviewErrorCode.REVIEW_ACCESS_DENIED)
        }
        if (review.isDeleted()) return null
        review.delete()
        reviewRepository.save(review)
        return review.productId
    }
}
