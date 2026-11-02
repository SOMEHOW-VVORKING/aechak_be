package com.aechak.api.review.response

import com.aechak.application.review.summary.usecase.result.ProductReviewSummaryResult
import com.aechak.application.review.summary.usecase.result.ReviewSummaryResult
import com.aechak.domain.review.summary.PetWeightBand
import com.aechak.domain.user.pet.enums.Species
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

data class ProductReviewSummaryResponse(
    @field:Schema(description = "상품 전체 리뷰 요약. 리뷰가 적거나 반복된 장단점이 없으면 null")
    val productSummary: AiReviewSummaryResponse?,
    @field:Schema(description = "조회자 대표 펫과 같은 종, 같은 체급 보호자들의 리뷰 요약. 펫이 없거나 체중을 모르거나 그 묶음 리뷰가 적으면 null")
    val similarPetSummary: SimilarPetAiReviewSummaryResponse?,
) {
    data class SimilarPetAiReviewSummaryResponse(
        val species: Species,
        val weightBand: PetWeightBand,
        val summary: AiReviewSummaryResponse,
    )

    data class AiReviewSummaryResponse(
        @field:Schema(description = "AI가 실제로 읽은 리뷰 수. 언급 수의 분모")
        val analyzedReviewCount: Int,
        val pros: List<AiReviewSummaryPointResponse>,
        val cons: List<AiReviewSummaryPointResponse>,
        @field:Schema(
            description =
                "cons가 비어 있는 사유. NO_NEGATIVE_MENTIONS_FOUND, BELOW_MENTION_THRESHOLD, " +
                    "PARTIALLY_UNEXTRACTED, NARRATION_MISSING 중 하나이고 cons가 있으면 null",
        )
        val consAbsenceReason: String?,
        val generatedAt: LocalDateTime?,
    )

    data class AiReviewSummaryPointResponse(
        val aspectCode: String,
        val headline: String,
        @field:Schema(description = "이 장단점을 말한 리뷰 수")
        val mentionCount: Int,
        @field:Schema(description = "리뷰에서 그대로 떼어 온 문장. 지금은 숨겨진 리뷰의 문장은 빠짐")
        val quotes: List<String>,
    )

    companion object {
        fun from(result: ProductReviewSummaryResult): ProductReviewSummaryResponse =
            ProductReviewSummaryResponse(
                productSummary = result.productSummary?.let(::summaryOf),
                similarPetSummary =
                    result.similarPetSummary?.let {
                        SimilarPetAiReviewSummaryResponse(it.species, it.weightBand, summaryOf(it.summary))
                    },
            )

        private fun summaryOf(result: ReviewSummaryResult): AiReviewSummaryResponse =
            AiReviewSummaryResponse(
                analyzedReviewCount = result.analyzedReviewCount,
                pros = result.pros.map { AiReviewSummaryPointResponse(it.aspectCode, it.headline, it.mentionCount, it.quotes) },
                cons = result.cons.map { AiReviewSummaryPointResponse(it.aspectCode, it.headline, it.mentionCount, it.quotes) },
                consAbsenceReason = result.consAbsenceReason,
                generatedAt = result.generatedAt,
            )
    }
}
