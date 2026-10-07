package com.aechak.api.review.request

import com.aechak.application.review.usecase.query.MyReviewListQuery
import com.aechak.application.review.usecase.query.MyReviewTab
import com.aechak.application.support.CursorPageSize
import com.aechak.common.error.BusinessException
import com.aechak.common.error.CommonErrorCode
import org.hibernate.validator.constraints.Range

data class MyReviewListRequest(
    val tab: String? = null,
    val cursor: String? = null,
    @field:Range(
        min = CursorPageSize.MIN,
        max = CursorPageSize.MAX,
        message = "size는 {min}~{max} 사이여야 합니다.",
    )
    val size: Int = CursorPageSize.DEFAULT,
) {
    fun toQuery(userId: Long) =
        MyReviewListQuery(
            userId = userId,
            tab = parseTab(),
            cursor = cursor,
            size = size,
        )

    private fun parseTab(): MyReviewTab =
        when (tab) {
            "written" -> MyReviewTab.WRITTEN
            "unreviewed" -> MyReviewTab.UNREVIEWED
            else -> throw BusinessException(CommonErrorCode.INVALID_REQUEST)
        }
}
