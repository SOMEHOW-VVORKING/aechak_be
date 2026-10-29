package com.aechak.application.review.summary.request.facade

import com.aechak.application.messaging.MessagePublisher
import com.aechak.application.review.summary.port.ReviewSummarySourceQueryPort
import com.aechak.application.review.summary.port.view.SummaryReviewStateView
import com.aechak.application.review.summary.request.service.ReviewSummaryRequestPlan
import com.aechak.application.review.summary.request.service.ReviewSummaryRequestPlanner
import com.aechak.application.review.summary.request.usecase.ReviewSummaryRequestUseCase
import com.aechak.domain.review.review.Review
import com.aechak.domain.review.summary.ReviewSummary
import com.aechak.domain.review.summary.ReviewSummaryRequest
import com.aechak.domain.review.summary.repository.ReviewSummaryRepository
import com.aechak.domain.review.summary.repository.ReviewSummaryRequestRepository
import com.aechak.message.review.ReviewSummaryRequestedMessage
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

@Service
class ReviewSummaryRequestFacade(
    private val reviewSummarySourceQueryPort: ReviewSummarySourceQueryPort,
    private val reviewSummaryRepository: ReviewSummaryRepository,
    private val reviewSummaryRequestRepository: ReviewSummaryRequestRepository,
    private val messagePublisher: MessagePublisher,
) : ReviewSummaryRequestUseCase {
    @Transactional(readOnly = true)
    override fun findRequestTargetProductIds(
        now: LocalDateTime,
        limit: Int,
    ): List<Long> = reviewSummarySourceQueryPort.findRequestTargetProductIds(ReviewSummary.MIN_PRODUCT_REVIEW_COUNT, now, limit)

    @Transactional
    override fun requestSummaryIfStale(
        productId: Long,
        now: LocalDateTime,
    ): Boolean {
        val reviews = reviewSummarySourceQueryPort.findVisibleReviewStates(productId)
        if (reviews.size < ReviewSummary.MIN_PRODUCT_REVIEW_COUNT) return false

        val request = reviewSummaryRequestRepository.findByProductId(productId) ?: ReviewSummaryRequest.create(productId)
        val basisSignatureByScopeKey = reviewSummaryRepository.findAllByProductId(productId).associate { it.scopeKey to it.basisSignature }
        return when (val plan = ReviewSummaryRequestPlanner.plan(reviews, basisSignatureByScopeKey)) {
            is ReviewSummaryRequestPlan.UpToDate -> {
                request.markSynced(plan.productSignature)
                reviewSummaryRequestRepository.save(request)
                false
            }

            is ReviewSummaryRequestPlan.Send -> {
                val message = buildMessage(productId, reviews, plan)
                request.start(message.requestId, plan.scopes.associate { it.scopeKey to it.expectedSignature }, now, RESPONSE_TIMEOUT)
                reviewSummaryRequestRepository.save(request)
                messagePublisher.publish(message)
                true
            }
        }
    }

    private fun buildMessage(
        productId: Long,
        reviews: List<SummaryReviewStateView>,
        plan: ReviewSummaryRequestPlan.Send,
    ): ReviewSummaryRequestedMessage {
        val aspects = reviewSummarySourceQueryPort.findExtractedAspectsOfVisibleReviews(productId)
        val newReviews =
            reviewSummarySourceQueryPort.findReviewContents(plan.newReviewIds).sortedBy { it.reviewId }.map {
                ReviewSummaryRequestedMessage.NewReview(
                    reviewId = it.reviewId,
                    rating = it.rating,
                    content = Review.visibleContent(it.reviewStatus, it.content, it.displayContent),
                    optionName = it.optionName,
                    hasPhoto = it.hasPhoto,
                    createdAt = it.createdAt.atZone(ZoneId.systemDefault()).toInstant(),
                )
            }
        return ReviewSummaryRequestedMessage(
            requestId = UUID.randomUUID().toString(),
            productId = productId,
            categoryPath = reviewSummarySourceQueryPort.findCategoryPath(productId),
            totalVisibleReviews = reviews.size,
            ratingBuckets =
                (1..5).map { rating -> ReviewSummaryRequestedMessage.RatingBucket(rating, reviews.count { it.rating == rating }) },
            newReviews = newReviews,
            requestedScopes =
                plan.scopes.map { scope ->
                    ReviewSummaryRequestedMessage.RequestedScope(
                        scope = scope.scope,
                        segmentKey = scope.segmentKey,
                        basisReviewCount = scope.basisReviewCount,
                        unextractedReviewCount = scope.unextractedReviewCount,
                        tally =
                            ReviewSummaryRequestPlanner.tallyOf(
                                aspects.filter {
                                    scope.segmentKey == null ||
                                        it.segmentKey == scope.segmentKey
                                },
                            ),
                    )
                },
            occurredAt = Instant.now(),
        )
    }

    companion object {
        // AI 서버의 요약 한 건 최악 처리 시간(폴 간격 15분)보다 넉넉히. 지나면 응답이 없던 요청을 다시 보냄
        private val RESPONSE_TIMEOUT: Duration = Duration.ofMinutes(30)
    }
}
