package com.aechak.application.auth.usecase.result

import com.aechak.domain.user.user.enums.UserStatus

data class SocialLoginResult(
    val tokens: TokenResult,
    /** PENDING_ONBOARDING이면 FE가 온보딩(약관·닉네임)으로 라우팅한다. */
    val userStatus: UserStatus,
    val isNew: Boolean,
)
