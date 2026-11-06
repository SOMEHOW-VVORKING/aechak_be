package com.aechak.api.sns.request

import com.aechak.application.sns.usecase.query.SnsCommentListQuery
import com.aechak.application.support.CursorPageSize
import io.swagger.v3.oas.annotations.media.Schema
import org.hibernate.validator.constraints.Range

data class SnsCommentListRequest(
    val cursor: String? = null,
    @field:Range(
        min = CursorPageSize.MIN,
        max = CursorPageSize.MAX,
        message = "size는 {min}~{max} 사이여야 합니다.",
    )
    @field:Schema(description = "한 페이지의 최상위 댓글 수. 그 아래 보이는 대댓글은 모두 함께 온다")
    val size: Int = CursorPageSize.DEFAULT,
) {
    fun toQuery(
        postPublicId: String,
        viewerId: Long?,
    ) = SnsCommentListQuery(postPublicId = postPublicId, viewerId = viewerId, cursor = cursor, size = size)
}
