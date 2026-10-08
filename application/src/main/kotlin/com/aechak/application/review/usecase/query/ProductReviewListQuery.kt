package com.aechak.application.review.usecase.query

import com.aechak.application.review.port.ReviewListSort
import com.aechak.application.support.CursorPageSize

data class ProductReviewListQuery(
    val productPublicId: String,
    val sort: ReviewListSort = ReviewListSort.LATEST,
    val photoOnly: Boolean = false,
    val cursor: String? = null,
    val size: Int = CursorPageSize.DEFAULT,
) {
    init {
        require(size in CursorPageSize.MIN..CursorPageSize.MAX) { "size는 ${CursorPageSize.MIN}~${CursorPageSize.MAX} 범위 안에 있어야 합니다." }
    }
}
