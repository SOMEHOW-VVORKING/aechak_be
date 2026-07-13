package com.aechak.api.auth.response

import com.aechak.application.auth.usecase.result.TokenResult

/**
 * 웹(쿠키) 채널의 토큰 응답 — refresh는 body가 아니라 Set-Cookie로 나가므로 여기 없다.
 * body의 refresh 부재가 곧 "JS는 refresh를 만질 수 없다"는 계약이다.
 */
data class WebTokenResponse(
    val accessToken: String,
    val tokenType: String = BEARER,
    val expiresIn: Long,
) {
    companion object {
        private const val BEARER = "Bearer"

        fun from(result: TokenResult) = WebTokenResponse(
            accessToken = result.accessToken,
            expiresIn = result.expiresInSeconds,
        )
    }
}
