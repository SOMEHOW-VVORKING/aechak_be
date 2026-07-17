package com.aechak.api.auth.response

import com.aechak.application.auth.usecase.result.TokenResult

/** 토큰 쌍 응답 — refresh token은 RN secure storage 전용(웹뷰 비전달). */
data class TokenPairResponse(
    val accessToken: String,
    val refreshToken: String,
    val tokenType: String = BEARER,
    val expiresIn: Long,
) {
    companion object {
        private const val BEARER = "Bearer"

        fun from(result: TokenResult) =
            TokenPairResponse(
                accessToken = result.accessToken,
                refreshToken = result.refreshToken,
                expiresIn = result.expiresInSeconds,
            )
    }
}
