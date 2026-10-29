package com.aechak.application.review.summary.usecase.result

import com.aechak.domain.review.summary.PetWeightBand
import com.aechak.domain.review.summary.ReviewSummary
import com.aechak.domain.review.summary.enums.AspectPolarity
import com.aechak.domain.user.pet.enums.Species
import java.time.LocalDateTime

data class ProductReviewSummaryResult(
    val productSummary: ReviewSummaryResult?,
    val similarPetSummary: SimilarPetReviewSummaryResult?,
) {
    companion object {
        val EMPTY = ProductReviewSummaryResult(null, null)
    }
}

data class SimilarPetReviewSummaryResult(
    val species: Species,
    val weightBand: PetWeightBand,
    val summary: ReviewSummaryResult,
)

data class ReviewSummaryResult(
    val analyzedReviewCount: Int,
    val pros: List<ReviewSummaryPointResult>,
    val cons: List<ReviewSummaryPointResult>,
    val consAbsenceReason: String?,
    val generatedAt: LocalDateTime?,
) {
    companion object {
        /** visibleReviewIds에 없는 리뷰의 인용은 뺌. 요약 뒤에 숨겨지거나 삭제된 리뷰의 문장을 보여 주지 않기 위함 */
        fun from(
            summary: ReviewSummary,
            visibleReviewIds: Set<Long>,
        ): ReviewSummaryResult {
            fun pointsOf(polarity: AspectPolarity) =
                summary.points.filter { it.polarity == polarity }.map { point ->
                    ReviewSummaryPointResult(
                        aspectCode = point.aspectCode,
                        headline = point.headline,
                        mentionCount = point.mentionCount,
                        quotes = point.quotes.filter { it.reviewId in visibleReviewIds }.map { it.text },
                    )
                }
            return ReviewSummaryResult(
                analyzedReviewCount = summary.analyzedReviewCount,
                pros = pointsOf(AspectPolarity.PRO),
                cons = pointsOf(AspectPolarity.CON),
                consAbsenceReason = summary.consAbsenceReason,
                generatedAt = summary.generatedAt,
            )
        }
    }
}

data class ReviewSummaryPointResult(
    val aspectCode: String,
    val headline: String,
    val mentionCount: Int,
    val quotes: List<String>,
)
