package com.aechak.application.sns.usecase.command

/** 댓글 수정 입력. userId는 principal이다 */
data class UpdateCommentCommand(
    val userId: Long,
    val commentPublicId: String,
    val content: String,
)
