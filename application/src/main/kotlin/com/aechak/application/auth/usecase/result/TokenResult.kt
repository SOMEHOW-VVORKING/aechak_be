package com.aechak.application.auth.usecase.result

data class TokenResult(
    val accessToken: String,
    val refreshToken: String,
    val refreshTokenId: String,
    val expiresInSeconds: Long,
)
