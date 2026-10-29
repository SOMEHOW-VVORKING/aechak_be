package com.aechak.application.review.summary.port

import com.aechak.application.review.summary.port.view.SummaryAspectView
import com.aechak.application.review.summary.port.view.SummaryReviewContentView
import com.aechak.application.review.summary.port.view.SummaryReviewStateView
import java.time.LocalDateTime

/** 리뷰 요약 요청에 쓰는 리뷰 조회. 노출 리뷰는 ReviewStatus.isVisible()인 리뷰 */
interface ReviewSummarySourceQueryPort {
    /**
     * 노출 상품 중 노출 리뷰가 minReviewCount 이상이고, 쉬는 중(retryAfter가 now 이후)이 아니며,
     * 지문이 ReviewSummaryRequest.syncedSignature와 다른 상품. 요청한 적 없는 상품, 오래전에 요청한 상품부터 limit개
     */
    fun findRequestTargetProductIds(
        minReviewCount: Int,
        now: LocalDateTime,
        limit: Int,
    ): List<Long>

    fun findVisibleReviewStates(productId: Long): List<SummaryReviewStateView>

    fun findReviewContents(reviewIds: Collection<Long>): List<SummaryReviewContentView>

    /** 추출에 성공한 노출 리뷰의 장단점 */
    fun findExtractedAspectsOfVisibleReviews(productId: Long): List<SummaryAspectView>

    /** 대분류부터 소분류 순 */
    fun findCategoryPath(productId: Long): List<String>
}
