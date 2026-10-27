package com.aechak.application.inspection.port

import com.aechak.application.inspection.port.view.ProductReviewQueueRowView
import com.aechak.application.inspection.port.view.ReviewTargetProductView
import com.aechak.application.support.PageQuery
import com.aechak.domain.inspection.enums.ReviewQueueStatus

interface ProductReviewQueueQueryPort {
    /** status가 null이면 전체. 판정 시각이 최근인 것부터 */
    fun findPage(
        status: ReviewQueueStatus?,
        pageQuery: PageQuery,
    ): List<ProductReviewQueueRowView>

    fun count(status: ReviewQueueStatus?): Long

    fun findReviewTarget(productPublicId: String): ReviewTargetProductView?
}
