package com.aechak.message.review

import java.time.Instant

/**
 * 리뷰 요약 결과(AI 서버 → 백엔드). 모양은 AI 서버 app/schemas/summary.py의 ReviewSummaryMessage와 같음
 * 백엔드가 쓰지 않는 필드(호출 원장, 버린 항목 수 등)는 받지 않음
 */
data class ReviewSummaryMessage(
    val requestId: String,
    val productId: Long,
    val state: State,
    val extractVersion: String,
    val narrateVersion: String,
    val extracted: List<ExtractedReview>,
    val summaries: List<ScopeSummary>,
    val failureDetail: String?,
    val generatedAt: Instant,
    val schemaVersion: Int,
) {
    enum class State {
        OK,
        FAILED,
    }

    enum class ExtractStatus {
        EXTRACTED,
        UNEXTRACTED,
    }

    data class ExtractedReview(
        val extracted: ExtractStatus,
        val reviewId: Long,
        val aspects: List<ExtractedAspect>,
        val excludeReason: String?,
    )

    data class ExtractedAspect(
        val aspectCode: String,
        val aspectLabel: String,
        val polarity: SummaryPolarity,
        val quote: String,
    )

    /**
     * 장단점이 비어도 되는 경우는 셋 -> consAbsenceReason이 있음, notEnoughReason이 있음, narrateFailure가 있음
     * narrateFailure가 있으면 모델 호출이 실패해 문장이 없는 것이라 요약으로 저장하지 않음
     */
    data class ScopeSummary(
        val scope: SummaryScope,
        val segmentKey: String?,
        val analyzedReviewCount: Int,
        val pros: List<SummaryPoint>,
        val cons: List<SummaryPoint>,
        val consAbsenceReason: String?,
        val notEnoughReason: String?,
        val narrateFailure: String?,
    )

    data class SummaryPoint(
        val aspectCode: String,
        val headline: String,
        val mentionCount: Int,
        val quotes: List<SummaryQuoteSample>,
    )

    companion object {
        const val SCHEMA_VERSION = 1
    }
}
