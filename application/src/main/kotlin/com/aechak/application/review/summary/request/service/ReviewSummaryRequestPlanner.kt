package com.aechak.application.review.summary.request.service

import com.aechak.application.review.summary.port.view.SummaryAspectView
import com.aechak.application.review.summary.port.view.SummaryReviewStateView
import com.aechak.domain.review.summary.ReviewSummary
import com.aechak.message.review.ReviewSummaryRequestedMessage
import com.aechak.message.review.SummaryPolarity
import com.aechak.message.review.SummaryQuoteSample
import com.aechak.message.review.SummaryScope

/**
 * 상품 하나에 무엇을 요청할지 정함
 *
 * 추출 안 된 리뷰가 있음 -> 그 리뷰(최대 MAX_NEW_REVIEWS)와 상품 전체 범위만 보냄
 * 전부 추출됨 -> 지문이 바뀐 범위만 보냄. 상품 전체가 먼저, 체급 묶음은 키 순, 최대 MAX_SCOPES_PER_REQUEST
 * 바뀐 범위도 없음 -> 보내지 않고 상품 지문을 최신으로 기록
 *
 * 체급 묶음을 추출과 함께 보내지 않는 이유는 AI 서버가 이번 메시지의 새 리뷰를 상품 전체 집계에만 더하기 때문
 */
object ReviewSummaryRequestPlanner {
    // AI 서버 계약의 newReviews 상한, summary_scopes_per_message와 같은 값
    const val MAX_NEW_REVIEWS = 40
    const val MAX_SCOPES_PER_REQUEST = 2

    private const val LABEL_SAMPLES_MAX = 3
    private const val QUOTE_SAMPLES_MAX = 3

    fun plan(
        reviews: List<SummaryReviewStateView>,
        basisSignatureByScopeKey: Map<String, String>,
    ): ReviewSummaryRequestPlan {
        val pendingReviews = reviews.filter { !it.settled }.sortedBy { it.reviewId }.take(MAX_NEW_REVIEWS)
        if (pendingReviews.isNotEmpty()) {
            val pendingReviewIds = pendingReviews.map { it.reviewId }.toSet()
            val productScope =
                PlannedScope(
                    scopeKey = ReviewSummary.PRODUCT_SCOPE_KEY,
                    segmentKey = null,
                    basisReviewCount = reviews.size,
                    unextractedReviewCount = reviews.count { !it.extracted && it.reviewId !in pendingReviewIds },
                    // 보낸 리뷰가 모두 결과를 받으면 될 지문
                    expectedSignature =
                        signatureOf(
                            reviews.size,
                            reviews.sumOf { it.reviewId },
                            xorOf(reviews),
                            reviews.count { it.settled } + pendingReviews.size,
                        ),
                )
            return ReviewSummaryRequestPlan.Send(pendingReviewIds.toList(), listOf(productScope))
        }

        val productSignature = signatureOf(reviews)
        val reviewsBySegmentKey = reviews.filter { it.segmentKey != null }.groupBy { checkNotNull(it.segmentKey) }
        // 요약이 이미 있는 묶음은 리뷰가 줄어도 다시 보냄. AI 서버가 리뷰 부족으로 답해야 화면에서 내려감
        val segmentKeys =
            (reviewsBySegmentKey.filterValues { it.size >= ReviewSummary.MIN_SEGMENT_REVIEW_COUNT }.keys + basisSignatureByScopeKey.keys)
                .minus(ReviewSummary.PRODUCT_SCOPE_KEY)
                .sorted()
        val staleScopes =
            buildList {
                if (basisSignatureByScopeKey[ReviewSummary.PRODUCT_SCOPE_KEY] != productSignature) {
                    add(fullyExtractedScope(ReviewSummary.PRODUCT_SCOPE_KEY, null, reviews))
                }
                segmentKeys.forEach { segmentKey ->
                    val members = reviewsBySegmentKey[segmentKey].orEmpty()
                    if (basisSignatureByScopeKey[segmentKey] != signatureOf(members)) {
                        add(fullyExtractedScope(segmentKey, segmentKey, members))
                    }
                }
            }
        if (staleScopes.isEmpty()) return ReviewSummaryRequestPlan.UpToDate(productSignature)
        return ReviewSummaryRequestPlan.Send(emptyList(), staleScopes.take(MAX_SCOPES_PER_REQUEST))
    }

    /**
     * 노출 리뷰 묶음의 지문. 리뷰가 하나 더해지거나 빠지거나 추출 결과가 확정되면 바뀜
     * 후보 조회 SQL(ReviewSummarySourceQueryAdapter.findRequestTargetProductIds)이 같은 모양을 만듦
     * ponytail: 수, 합, XOR가 모두 같은 다른 리뷰 묶음은 구분 못 함. 실제로 문제가 되면 상품별 revision 카운터로 바꿈
     */
    fun signatureOf(
        reviewCount: Int,
        reviewIdSum: Long,
        reviewIdXor: Long,
        settledReviewCount: Int,
    ): String = "$reviewCount:$reviewIdSum:$reviewIdXor:$settledReviewCount"

    /** 지문의 확정 수를 settledReviewCount 이하로 낮춤 */
    fun capSettledReviewCount(
        signature: String,
        settledReviewCount: Int,
    ): String {
        val (reviewCount, reviewIdSum, reviewIdXor, expectedSettledCount) = signature.split(":")
        return signatureOf(
            reviewCount.toInt(),
            reviewIdSum.toLong(),
            reviewIdXor.toLong(),
            minOf(expectedSettledCount.toInt(), settledReviewCount),
        )
    }

    private fun signatureOf(reviews: List<SummaryReviewStateView>): String =
        signatureOf(reviews.size, reviews.sumOf { it.reviewId }, xorOf(reviews), reviews.count { it.settled })

    private fun xorOf(reviews: List<SummaryReviewStateView>): Long = reviews.fold(0L) { acc, review -> acc xor review.reviewId }

    private fun fullyExtractedScope(
        scopeKey: String,
        segmentKey: String?,
        members: List<SummaryReviewStateView>,
    ): PlannedScope =
        PlannedScope(
            scopeKey = scopeKey,
            segmentKey = segmentKey,
            basisReviewCount = members.size,
            unextractedReviewCount = members.count { !it.extracted },
            expectedSignature = signatureOf(members),
        )

    /** 언급 많은 순, 같으면 항목 코드 순. 표본은 최근 리뷰부터 */
    fun tallyOf(aspects: List<SummaryAspectView>): List<ReviewSummaryRequestedMessage.TallyRow> =
        aspects
            .groupBy { it.aspectCode to it.polarity }
            .map { (key, rows) ->
                val newestFirst = rows.sortedByDescending { it.reviewId }
                ReviewSummaryRequestedMessage.TallyRow(
                    aspectCode = key.first,
                    polarity = SummaryPolarity.valueOf(key.second.name),
                    mentionCount = rows.distinctBy { it.reviewId }.size,
                    labelSamples = newestFirst.map { it.label }.distinct().take(LABEL_SAMPLES_MAX),
                    quoteSamples =
                        newestFirst
                            .distinctBy { it.reviewId to it.quote }
                            .take(QUOTE_SAMPLES_MAX)
                            .map { SummaryQuoteSample(it.reviewId, it.quote) },
                )
            }.sortedWith(
                compareByDescending<ReviewSummaryRequestedMessage.TallyRow> {
                    it.mentionCount
                }.thenBy { it.aspectCode }.thenBy { it.polarity },
            )
}

sealed interface ReviewSummaryRequestPlan {
    data class Send(
        val newReviewIds: List<Long>,
        val scopes: List<PlannedScope>,
    ) : ReviewSummaryRequestPlan

    data class UpToDate(
        val productSignature: String,
    ) : ReviewSummaryRequestPlan
}

data class PlannedScope(
    val scopeKey: String,
    val segmentKey: String?,
    val basisReviewCount: Int,
    val unextractedReviewCount: Int,
    val expectedSignature: String,
) {
    val scope: SummaryScope get() = if (segmentKey == null) SummaryScope.PRODUCT else SummaryScope.SEGMENT
}
