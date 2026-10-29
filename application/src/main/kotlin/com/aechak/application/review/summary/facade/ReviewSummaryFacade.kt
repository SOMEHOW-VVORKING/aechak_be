package com.aechak.application.review.summary.facade

import com.aechak.application.product.product.usecase.ProductUseCase
import com.aechak.application.review.summary.port.ReviewSummarySourceQueryPort
import com.aechak.application.review.summary.usecase.ReviewSummaryUseCase
import com.aechak.application.review.summary.usecase.command.ApplyReviewSummaryResultCommand
import com.aechak.application.review.summary.usecase.result.ProductReviewSummaryResult
import com.aechak.application.review.summary.usecase.result.ReviewSummaryResult
import com.aechak.application.review.summary.usecase.result.SimilarPetReviewSummaryResult
import com.aechak.application.user.pet.usecase.PetProfileUseCase
import com.aechak.domain.review.summary.PetWeightBand
import com.aechak.domain.review.summary.ReviewAspect
import com.aechak.domain.review.summary.ReviewAspectExtraction
import com.aechak.domain.review.summary.ReviewSummary
import com.aechak.domain.review.summary.ReviewSummaryPoint
import com.aechak.domain.review.summary.ReviewSummaryQuote
import com.aechak.domain.review.summary.enums.AspectPolarity
import com.aechak.domain.review.summary.repository.ReviewAspectExtractionRepository
import com.aechak.domain.review.summary.repository.ReviewSummaryRepository
import com.aechak.domain.review.summary.repository.ReviewSummaryRequestRepository
import com.aechak.message.review.ReviewSummaryMessage
import com.aechak.message.review.SummaryScope
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId

@Service
class ReviewSummaryFacade(
    private val reviewSummarySourceQueryPort: ReviewSummarySourceQueryPort,
    private val reviewAspectExtractionRepository: ReviewAspectExtractionRepository,
    private val reviewSummaryRepository: ReviewSummaryRepository,
    private val reviewSummaryRequestRepository: ReviewSummaryRequestRepository,
    private val productUseCase: ProductUseCase,
    private val petProfileUseCase: PetProfileUseCase,
    @Value("\${review.summary.exposed:true}") private val reviewSummaryExposed: Boolean,
) : ReviewSummaryUseCase {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    override fun applyResult(command: ApplyReviewSummaryResultCommand): Boolean {
        val message = command.message
        saveExtractions(message)

        val request = reviewSummaryRequestRepository.findByProductId(message.productId)
        if (request == null || !request.isLatest(message.requestId)) return false

        val now = LocalDateTime.now()
        if (message.state == ReviewSummaryMessage.State.FAILED) {
            log.warn("리뷰 요약 실패 응답. productId={}, requestId={}, detail={}", message.productId, message.requestId, message.failureDetail)
            request.backOff(now, FAILURE_BACKOFF)
            reviewSummaryRequestRepository.save(request)
            return true
        }

        var narrationFailed = false
        message.summaries.forEach { scopeSummary ->
            val scopeKey = scopeKeyOf(scopeSummary) ?: return@forEach
            // 문장을 못 쓴 범위는 기존 요약을 그대로 둠. 지문이 그대로라 쉬는 시간이 지나면 다시 요청함
            if (scopeSummary.narrateFailure != null) {
                narrationFailed = true
                return@forEach
            }
            val expectedSignature = request.findExpectedSignature(scopeKey) ?: return@forEach
            val summary =
                reviewSummaryRepository.findByProductIdAndScopeKey(message.productId, scopeKey)
                    ?: ReviewSummary.create(message.productId, scopeKey)
            summary.refresh(
                basisSignature = expectedSignature,
                analyzedReviewCount = scopeSummary.analyzedReviewCount,
                enoughReviews = scopeSummary.notEnoughReason == null && scopeSummary.consAbsenceReason != NOT_ENOUGH_REVIEWS,
                consAbsenceReason = scopeSummary.consAbsenceReason,
                points = pointsOf(scopeSummary),
                requestId = message.requestId,
                generatedAt = LocalDateTime.ofInstant(message.generatedAt, ZoneId.systemDefault()),
            )
            reviewSummaryRepository.save(summary)
        }
        if (narrationFailed) request.backOff(now, FAILURE_BACKOFF) else request.complete()
        reviewSummaryRequestRepository.save(request)
        return true
    }

    @Transactional(readOnly = true)
    override fun getProductReviewSummary(
        productPublicId: String,
        viewerUserId: Long?,
    ): ProductReviewSummaryResult {
        val productId = productUseCase.getVisibleProductId(productPublicId)
        if (!reviewSummaryExposed) return ProductReviewSummaryResult.EMPTY
        // 리뷰가 숨겨져 기준 아래로 내려간 상품은 배치가 다시 고르지 않아 저장된 요약이 남아 있음
        if (reviewSummarySourceQueryPort.countVisibleReviews(productId) < ReviewSummary.MIN_PRODUCT_REVIEW_COUNT) {
            return ProductReviewSummaryResult.EMPTY
        }

        val summaryByScopeKey = reviewSummaryRepository.findAllByProductId(productId).associateBy { it.scopeKey }
        val productSummary = summaryByScopeKey[ReviewSummary.PRODUCT_SCOPE_KEY]?.takeIf { it.isDisplayable() }
        val viewerWeightBand =
            viewerUserId
                ?.let { petProfileUseCase.findRepresentativePet(it) }
                ?.let { PetWeightBand.of(it.species, it.weight) }
        val segmentSummary = viewerWeightBand?.let { summaryByScopeKey[it.segmentKey] }?.takeIf { it.isDisplayable() }

        val quotedReviewIds = listOfNotNull(productSummary, segmentSummary).flatMap { it.points }.flatMap { it.quotes }.map { it.reviewId }
        // 다른 상품 리뷰의 문장이 섞여 오면 이 상품 후기처럼 보이므로 상품 소속까지 확인함
        val visibleReviewIds = reviewSummarySourceQueryPort.findVisibleReviewIdsOfProduct(productId, quotedReviewIds)
        return ProductReviewSummaryResult(
            productSummary = productSummary?.let { ReviewSummaryResult.from(it, visibleReviewIds) },
            similarPetSummary =
                if (viewerWeightBand != null && segmentSummary != null) {
                    SimilarPetReviewSummaryResult(
                        species = viewerWeightBand.species,
                        weightBand = viewerWeightBand,
                        summary = ReviewSummaryResult.from(segmentSummary, visibleReviewIds),
                    )
                } else {
                    null
                },
        )
    }

    private fun saveExtractions(message: ReviewSummaryMessage) {
        val ownReviewIds =
            reviewSummarySourceQueryPort.findReviewIdsOfProduct(message.productId, message.extracted.map { it.reviewId })
        val extractionByReviewId = reviewAspectExtractionRepository.findAllByReviewIds(ownReviewIds).associateBy { it.reviewId }
        val extractions =
            message.extracted.filter { it.reviewId in ownReviewIds }.distinctBy { it.reviewId }.map { extractedReview ->
                val extraction =
                    extractionByReviewId[extractedReview.reviewId]
                        ?: ReviewAspectExtraction.create(extractedReview.reviewId, message.productId)
                when (extractedReview.extracted) {
                    ReviewSummaryMessage.ExtractStatus.EXTRACTED -> {
                        extraction.recordExtracted(
                            extractedReview.aspects.map {
                                ReviewAspect(it.aspectCode, AspectPolarity.valueOf(it.polarity.name), it.aspectLabel, it.quote)
                            },
                            message.extractVersion,
                        )
                    }

                    ReviewSummaryMessage.ExtractStatus.UNEXTRACTED -> {
                        extraction.recordUnextracted(extractedReview.excludeReason, message.extractVersion)
                    }
                }
                extraction
            }
        reviewAspectExtractionRepository.saveAll(extractions)
    }

    private fun scopeKeyOf(scopeSummary: ReviewSummaryMessage.ScopeSummary): String? =
        when (scopeSummary.scope) {
            SummaryScope.PRODUCT -> ReviewSummary.PRODUCT_SCOPE_KEY
            SummaryScope.SEGMENT -> scopeSummary.segmentKey
        }

    private fun pointsOf(scopeSummary: ReviewSummaryMessage.ScopeSummary): List<ReviewSummaryPoint> {
        val polarityPoints =
            scopeSummary.pros.map { AspectPolarity.PRO to it } + scopeSummary.cons.map { AspectPolarity.CON to it }
        return polarityPoints.mapIndexed { index, (polarity, point) ->
            ReviewSummaryPoint.of(
                polarity = polarity,
                aspectCode = point.aspectCode,
                headline = point.headline,
                mentionCount = point.mentionCount,
                sortOrder = index,
                quotes = point.quotes.map { ReviewSummaryQuote(it.reviewId, it.text) },
            )
        }
    }

    companion object {
        private const val NOT_ENOUGH_REVIEWS = "NOT_ENOUGH_REVIEWS"

        // 같은 실패로 유료 호출을 매시간 되풀이하지 않게 쉼
        private val FAILURE_BACKOFF: Duration = Duration.ofHours(6)
    }
}
