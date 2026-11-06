package com.aechak.application.sns.usecase

import com.aechak.application.sns.usecase.command.CreateCommentCommand
import com.aechak.application.sns.usecase.command.UpdateCommentCommand
import com.aechak.application.sns.usecase.query.SnsCommentListQuery
import com.aechak.application.sns.usecase.result.SnsCommentItemResult
import com.aechak.application.support.CursorPageResult

interface SnsCommentUseCase {
    // 댓글 목록. 한 페이지는 최상위 댓글 size개와 그 아래 보이는 대댓글 전부다
    fun getComments(query: SnsCommentListQuery): CursorPageResult<SnsCommentItemResult>

    // 댓글을 쓰고 쓴 사람 기준 항목을 돌려준다. 같은 작성자가 같은 게시물에 같은 멱등키로 쓴 댓글이 있으면 그 댓글의 지금 항목이다
    fun createComment(command: CreateCommentCommand): SnsCommentItemResult

    // 본인 댓글 내용을 바꾸고 바뀐 항목을 돌려준다. 같은 내용이면 수정 시각을 그대로 둔다
    fun updateComment(command: UpdateCommentCommand): SnsCommentItemResult

    // 본인 댓글을 지운다. 이미 지운 댓글도 그대로 끝나고 게시물 판정은 하지 않는다
    fun deleteComment(
        userId: Long,
        commentPublicId: String,
    )
}
