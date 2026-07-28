package com.aechak.application.user.user.usecase.result

import com.aechak.domain.user.user.enums.UserStatus

/** 내 정보 — PENDING이면 프로필 계열은 null, email은 소셜 미제공(애플 가림 등)이면 null. */
data class UserMeResult(
    val status: UserStatus,
    val nickname: String?,
    val profileImageUrl: String?,
    val bio: String?,
    val email: String?,
    val isPhoneVerified: Boolean,
)
