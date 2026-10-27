package com.aechak.application.sns.usecase.command

/** 뮤트와 뮤트 해제 입력 */
data class MuteUserCommand(
    val userId: Long,
    val targetPublicId: String,
)
