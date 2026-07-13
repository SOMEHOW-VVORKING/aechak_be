package com.aechak.api.auth.response

import com.aechak.application.auth.usecase.result.SocialLoginResult

/** contracts/auth.yaml LoginResult(TokenPair + user). */
data class LoginResponse(
    val accessToken: String,
    val refreshToken: String,
    val tokenType: String,
    val expiresIn: Long,
    val user: LoginUserResponse,
) {
    companion object {
        fun from(result: SocialLoginResult): LoginResponse {
            val tokens = TokenPairResponse.from(result.tokens)
            return LoginResponse(
                accessToken = tokens.accessToken,
                refreshToken = tokens.refreshToken,
                tokenType = tokens.tokenType,
                expiresIn = tokens.expiresIn,
                user = LoginUserResponse(result.userStatus.name, result.isNew),
            )
        }
    }
}
