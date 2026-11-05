package com.aechak.message.review

import com.aechak.message.BestEffortMessage
import java.time.Instant

/**
 * 리뷰 요약 요청(백엔드 → AI 서버). 모양은 AI 서버 app/schemas/summary.py의 ReviewSummaryRequest와 같음
 * 잃어도 응답 대기 시간이 지나면 다음 배치가 같은 상품을 다시 요청하므로 유실 가능 등급
 */
data class ReviewSummaryRequestedMessage(
    val requestId: String,
    val productId: Long,
    val categoryPath: List<String>,
    val totalVisibleReviews: Int,
    val ratingBuckets: List<RatingBucket>,
    val newReviews: List<NewReview>,
    val requestedScopes: List<RequestedScope>,
    override val occurredAt: Instant,
    val schemaVersion: Int = SCHEMA_VERSION,
) : BestEffortMessage {
    data class RatingBucket(
        val rating: Int,
        val count: Int,
    )

    /** content는 Review.visibleContent 결과. 원문을 실으면 마스킹한 금칙어가 요약에서 되살아남 */
    data class NewReview(
        val reviewId: Long,
        val rating: Int,
        val content: String,
        val optionName: String,
        val hasPhoto: Boolean,
        val createdAt: Instant,
    )

    /**
     * basisReviewCount -> 이 범위의 노출 리뷰 수. PRODUCT는 이 메시지의 newReviews를 포함함
     * unextractedReviewCount -> 이 범위에서 추출 결과가 없는 리뷰 수. 이 메시지의 newReviews는 뺌
     */
    data class RequestedScope(
        val scope: SummaryScope,
        val segmentKey: String?,
        val basisReviewCount: Int,
        val unextractedReviewCount: Int,
        val tally: List<TallyRow>,
    )

    data class TallyRow(
        val aspectCode: String,
        val polarity: SummaryPolarity,
        val mentionCount: Int,
        val labelSamples: List<String>,
        val quoteSamples: List<SummaryQuoteSample>,
    )

    override val aggregateType: String get() = AGGREGATE_TYPE
    override val orderingKey: String get() = productId.toString()
    override val eventId: String get() = "review-summary-request-$requestId"

    companion object {
        /** Topics가 이 값을 AI 서버가 정한 요청 토픽으로 바꿈 */
        const val AGGREGATE_TYPE = "reviewSummary"
        const val SCHEMA_VERSION = 1
    }
}
