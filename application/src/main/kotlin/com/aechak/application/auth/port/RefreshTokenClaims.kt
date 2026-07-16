package com.aechak.application.auth.port

import java.time.Instant

/** 검증을 통과한 자체 refresh token의 클레임 — TokenCodec.decodeRefreshToken의 반환 어휘. */
data class RefreshTokenClaims(
    val userId: Long,
    val role: String,
    val tokenId: String,
    val issuedAt: Instant,
    val expiresAt: Instant,
)
