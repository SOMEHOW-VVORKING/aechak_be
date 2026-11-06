package com.aechak.application.sns.usecase.command

/** 댓글 작성 입력. userId는 principal이고 parentPublicId가 있으면 대댓글이다 */
data class CreateCommentCommand(
    val userId: Long,
    val postPublicId: String,
    val idempotencyKey: String,
    val content: String,
    val parentPublicId: String?,
)
