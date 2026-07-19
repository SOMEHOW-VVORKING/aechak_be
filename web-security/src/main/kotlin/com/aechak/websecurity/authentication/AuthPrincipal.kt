package com.aechak.websecurity.authentication

import com.aechak.domain.user.user.enums.UserRole

/** 인증된 요청 주체 — 컨트롤러는 @AuthenticationPrincipal로 받는다. */
data class AuthPrincipal(
    val userId: Long,
    val role: UserRole,
)
