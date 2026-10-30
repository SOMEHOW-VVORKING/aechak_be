package com.aechak.application.sns.usecase.command

/** 차단과 차단 해제 입력 */
data class BlockUserCommand(
    val userId: Long,
    val targetPublicId: String,
)
