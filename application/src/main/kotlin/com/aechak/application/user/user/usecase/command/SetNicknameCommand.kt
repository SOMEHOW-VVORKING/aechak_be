package com.aechak.application.user.user.usecase.command

data class SetNicknameCommand(
    val userId: Long,
    val nickname: String,
)
