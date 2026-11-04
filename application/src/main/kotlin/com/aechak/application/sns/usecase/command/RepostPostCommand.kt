package com.aechak.application.sns.usecase.command

/** 리포스트 켜기와 끄기 입력 */
data class RepostPostCommand(
    val userId: Long,
    val publicId: String,
)
