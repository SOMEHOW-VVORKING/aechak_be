package com.aechak.application.review.usecase.query

import com.aechak.application.support.CursorPageSize

enum class MyReviewTab {
    WRITTEN,
    UNREVIEWED,
}

data class MyReviewListQuery(
    val userId: Long,
    val tab: MyReviewTab,
    val cursor: String? = null,
    val size: Int = CursorPageSize.DEFAULT,
) {
    init {
        require(size in CursorPageSize.MIN..CursorPageSize.MAX) { "size는 ${CursorPageSize.MIN}~${CursorPageSize.MAX} 범위 안에 있어야 합니다." }
    }
}
