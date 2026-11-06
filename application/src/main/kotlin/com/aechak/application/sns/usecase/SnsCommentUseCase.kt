package com.aechak.application.sns.usecase

import com.aechak.application.sns.usecase.query.SnsCommentListQuery
import com.aechak.application.sns.usecase.result.SnsCommentItemResult
import com.aechak.application.support.CursorPageResult

interface SnsCommentUseCase {
    // 댓글 목록. 한 페이지는 최상위 댓글 size개와 그 아래 보이는 대댓글 전부다
    fun getComments(query: SnsCommentListQuery): CursorPageResult<SnsCommentItemResult>
}
