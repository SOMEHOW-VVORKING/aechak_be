package com.aechak.api.sns.request

import com.aechak.application.sns.usecase.command.UpdateCommentCommand
import io.swagger.v3.oas.annotations.media.Schema

/** 댓글 수정 요청. content를 통째로 바꾸고 없거나 null이면 90001이다 */
data class UpdateCommentRequest(
    @field:Schema(description = "작성과 같은 규칙의 댓글 내용")
    val content: String,
) {
    fun toCommand(
        userId: Long,
        commentPublicId: String,
    ): UpdateCommentCommand = UpdateCommentCommand(userId = userId, commentPublicId = commentPublicId, content = content)
}
