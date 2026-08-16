package com.aechak.application.review.moderation.facade

import com.aechak.application.messaging.MessagePublisher
import com.aechak.application.review.moderation.service.ReviewModerationDecision
import com.aechak.application.review.moderation.service.ReviewModerationService
import com.aechak.application.review.moderation.usecase.ReviewModerationUseCase
import com.aechak.domain.review.review.enums.ReviewStatus
import com.aechak.domain.review.review.repository.ReviewRepository
import com.aechak.message.review.ReviewBlockedMessage
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ReviewModerationFacade(
    private val reviewRepository: ReviewRepository,
    private val reviewModerationService: ReviewModerationService,
    private val messagePublisher: MessagePublisher,
) : ReviewModerationUseCase {
    /** 리뷰 내용을 판정해 마스킹하거나 차단 */
    @Transactional
    override fun moderate(reviewId: Long) {
        val review = reviewRepository.findById(reviewId) ?: return
        if (review.reviewStatus != ReviewStatus.PUBLIC) return

        when (val decision = reviewModerationService.decide(review.content)) {
            is ReviewModerationDecision.Keep -> {}

            is ReviewModerationDecision.Mask -> {
                reviewRepository.maskIfPublic(reviewId, decision.displayContent)
            }

            is ReviewModerationDecision.Block -> {
                if (reviewRepository.blockIfPublic(reviewId) > 0) {
                    messagePublisher.publish(ReviewBlockedMessage(reviewId = reviewId, productId = review.productId))
                }
            }
        }
    }
}
