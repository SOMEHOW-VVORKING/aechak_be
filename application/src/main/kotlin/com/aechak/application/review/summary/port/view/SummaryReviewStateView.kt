package com.aechak.application.review.summary.port.view

/**
 * extracted -> 추출 결과가 있음
 * settled -> 다시 보낼 필요가 없음. ReviewAspectExtraction.isSettled()와 같은 판정
 */
data class SummaryReviewStateView(
    val reviewId: Long,
    val rating: Int,
    val segmentKey: String?,
    val extracted: Boolean,
    val settled: Boolean,
)
