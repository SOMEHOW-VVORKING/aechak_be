package com.aechak.application.user.user.usecase.result

data class UserAuthorResult(
    val userId: Long,
    val nickname: String,
    val withdrawn: Boolean,
    val profileImageUrl: String?,
)
