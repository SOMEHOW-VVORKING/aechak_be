package com.aechak.application.review.service

import com.aechak.application.order.usecase.result.OrderItemForReviewResult
import com.aechak.application.review.moderation.service.ReviewModerationDecision
import com.aechak.application.review.usecase.command.CreateReviewCommand
import com.aechak.common.error.BusinessException
import com.aechak.domain.review.error.ReviewErrorCode
import com.aechak.domain.review.reply.repository.ReviewReplyRepository
import com.aechak.domain.review.review.Review
import com.aechak.domain.review.review.ReviewImage
import com.aechak.domain.review.review.repository.DuplicateOrderItemReviewException
import com.aechak.domain.review.review.repository.ReviewRepository
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class ReviewCommandService(
    private val reviewRepository: ReviewRepository,
    private val reviewReplyRepository: ReviewReplyRepository,
) {
    fun ensureCanCreateReview(
        command: CreateReviewCommand,
        orderItem: OrderItemForReviewResult,
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
        orderItem: OrderItemForReviewResult,
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
        // 리뷰가 사라지면 셀러 답글이 붙을 자리가 없어진다. 리뷰는 소프트 삭제라 DB FK로는 정리되지 않으므로
        // 삭제 전이와 같은 트랜잭션에서 지운다. 이미 지워진 리뷰로 다시 들어와도 남은 행을 정리한다.
        if (review.isDeleted()) {
            reviewReplyRepository.deleteByReviewId(reviewId)
            return null
        }
        review.delete()
        // save가 saveAndFlush라 이 줄에서 reviews 행에 쓰기 잠금이 걸린다. 답글 정리를 **그 뒤로** 둬야
        // 같은 순간에 들어온 답글 등록이 이 정리보다 먼저 끼어들지 못한다 — 답글 쓰기 경로도 reviews를
        // 먼저 잠그므로, 둘 중 어느 쪽이 먼저 잠그든 결과가 하나로 모인다(고아 답글이 남지 않는다).
        reviewRepository.save(review)
        reviewReplyRepository.deleteByReviewId(reviewId)
        return review.productId
    }
}
