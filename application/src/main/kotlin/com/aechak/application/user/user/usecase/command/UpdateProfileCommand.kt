package com.aechak.application.user.user.usecase.command

/** 전체 교체 — 세 필드를 항상 다 받는다(유지할 값도 실어 보냄). nullable은 null=제거. */
data class UpdateProfileCommand(
    val userId: Long,
    val nickname: String,
    val bio: String?,
    val profileImageKey: String?,
)
