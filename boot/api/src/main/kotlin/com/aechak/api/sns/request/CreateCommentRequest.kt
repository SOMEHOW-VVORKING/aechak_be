package com.aechak.api.sns.request

import com.aechak.application.sns.usecase.command.CreateCommentCommand
import io.swagger.v3.oas.annotations.media.Schema

/** 댓글 작성 요청. content가 없거나 null이면 90001이고 길이와 공백은 서버 규칙(130702, 130703)으로 본다 */
data class CreateCommentRequest(
    @field:Schema(description = "댓글 내용. NFC 정규화와 앞뒤 공백 정리 뒤 코드 포인트 500자까지")
    val content: String,
    @field:Schema(description = "답글달기를 누른 댓글의 publicId. 대댓글을 가리키면 그 최상위 댓글 아래 붙는다. 최상위 댓글이면 생략하거나 null")
    val parentPublicId: String? = null,
) {
    fun toCommand(
        userId: Long,
        postPublicId: String,
        idempotencyKey: String,
    ): CreateCommentCommand =
        CreateCommentCommand(
            userId = userId,
            postPublicId = postPublicId,
            idempotencyKey = idempotencyKey,
            content = content,
            parentPublicId = parentPublicId,
        )
}
