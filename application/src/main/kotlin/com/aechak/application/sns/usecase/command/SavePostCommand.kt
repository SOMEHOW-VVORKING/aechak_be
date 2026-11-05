package com.aechak.application.sns.usecase.command

/** 저장 켜기와 끄기 입력 */
data class SavePostCommand(
    val userId: Long,
    val publicId: String,
)
