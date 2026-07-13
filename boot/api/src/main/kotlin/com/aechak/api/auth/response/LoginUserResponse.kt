package com.aechak.api.auth.response

data class LoginUserResponse(
    /** PENDING_ONBOARDING이면 FE가 온보딩(약관·닉네임)으로 라우팅한다. */
    val status: String,
    val isNew: Boolean,
)
