package com.aechak.application.sns.usecase.command

/** 팔로우와 언팔로우 입력 */
data class FollowUserCommand(
    val userId: Long,
    val targetPublicId: String,
)
