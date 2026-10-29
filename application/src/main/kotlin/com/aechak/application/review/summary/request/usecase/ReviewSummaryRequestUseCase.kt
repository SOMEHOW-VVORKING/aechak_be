package com.aechak.application.review.summary.request.usecase

import java.time.LocalDateTime

interface ReviewSummaryRequestUseCase {
    /** 노출 리뷰가 마지막 확인 뒤로 바뀐 상품. 요청한 적 없는 상품, 오래전에 요청한 상품부터 */
    fun findRequestTargetProductIds(
        now: LocalDateTime,
        limit: Int,
    ): List<Long>

    /** 요청할 것이 있으면 AI 서버에 보내고 true. 모든 범위가 최신이면 그렇게 기록하고 false */
    fun requestSummaryIfStale(
        productId: Long,
        now: LocalDateTime,
    ): Boolean
}
