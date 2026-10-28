package com.aechak.application.inspection.port

import com.aechak.application.inspection.port.view.ProductReviewQueueRowView
import com.aechak.application.inspection.port.view.ReviewTargetProductView
import com.aechak.application.support.PageQuery
import com.aechak.domain.inspection.enums.ReviewQueueStatus

interface ProductReviewQueueQueryPort {
    /**
     * status가 null이면 전체. 판정 시각이 최근인 것부터.
     *
     * [appealedOnly]는 셀러 이의가 걸린 행만 본다. 등재 사유의 한 축이 아니라 별 축이다 —
     * 대개 이미 AI가 가려 열린 행에 이의가 붙으므로, 등재 사유를 덮으면 경로별 유입량을 셀 수 없다.
     */
    fun findPage(
        status: ReviewQueueStatus?,
        appealedOnly: Boolean,
        pageQuery: PageQuery,
    ): List<ProductReviewQueueRowView>

    fun count(
        status: ReviewQueueStatus?,
        appealedOnly: Boolean,
    ): Long

    fun findReviewTarget(productPublicId: String): ReviewTargetProductView?
}
