package com.aechak.application.sns.usecase.query

import com.aechak.application.support.CursorPageSize

/** 댓글 목록 입력. viewerId가 null이면 비로그인이고 size는 최상위 댓글 수다 */
data class SnsCommentListQuery(
    val postPublicId: String,
    val viewerId: Long?,
    val cursor: String?,
    val size: Int = CursorPageSize.DEFAULT,
) {
    init {
        require(size in CursorPageSize.MIN..CursorPageSize.MAX) { "size는 ${CursorPageSize.MIN}~${CursorPageSize.MAX} 범위 안에 있어야 합니다." }
    }
}
