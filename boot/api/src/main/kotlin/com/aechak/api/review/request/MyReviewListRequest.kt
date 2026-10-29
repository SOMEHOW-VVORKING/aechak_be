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

    /**
     * 누락과 어휘 밖의 값을 가르고 파라미터 이름을 싣는다 — 타입 있는 필수 파라미터는 프레임워크 예외가
     * 그 둘을 알려 주지만, 여기는 널 허용 문자열로 받아 직접 판정하므로 같은 정보량을 손으로 채워야 한다.
     */
    private fun parseTab(): MyReviewTab =
        when (tab) {
            "written" -> MyReviewTab.WRITTEN
            "unreviewed" -> MyReviewTab.UNREVIEWED
            null -> throw BusinessException(CommonErrorCode.INVALID_REQUEST, detail = "tab: 필수 파라미터가 누락되었습니다.")
            else -> throw BusinessException(CommonErrorCode.INVALID_REQUEST, detail = "tab: 잘못된 값입니다.")
        }
}
