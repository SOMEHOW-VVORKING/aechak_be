package com.aechak.api.auth.response

import com.aechak.application.auth.usecase.result.SocialLoginResult

/** 로그인 응답 — 토큰 쌍 + 유저 온보딩 상태(FE 라우팅용). */
data class LoginResponse(
    val accessToken: String,
    val refreshToken: String,
    val tokenType: String,
    val expiresIn: Long,
    val user: User,
) {
    data class User(
        /** PENDING_ONBOARDING이면 FE가 온보딩(약관·닉네임)으로 라우팅한다. */
        val status: String,
        val isNew: Boolean,
    )

    companion object {
        fun from(result: SocialLoginResult): LoginResponse {
            val tokens = TokenPairResponse.from(result.tokens)
            return LoginResponse(
                accessToken = tokens.accessToken,
                refreshToken = tokens.refreshToken,
                tokenType = tokens.tokenType,
                expiresIn = tokens.expiresIn,
                user = User(result.userStatus.name, result.isNew),
            )
        }
    }
}
