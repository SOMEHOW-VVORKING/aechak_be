package com.aechak.application.sns.usecase.command

/** 게시물 좋아요 켜기와 끄기 입력 */
data class LikePostCommand(
    val userId: Long,
    val publicId: String,
)
