package com.aechak.application.sns.usecase.command

/** 댓글 좋아요 켜기, 끄기, 좋아요한 사람 입력 */
data class LikeCommentCommand(
    val userId: Long,
    val commentPublicId: String,
)
