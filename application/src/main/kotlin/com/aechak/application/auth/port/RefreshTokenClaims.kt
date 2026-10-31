package com.aechak.application.auth.port

import java.time.Instant

/**
 * 검증을 통과한 자체 refresh token의 클레임 — TokenCodec.decodeRefreshToken의 반환 어휘.
 *
 * 역할은 담지 않는다. 회전은 DB에서 역할을 다시 읽으므로 토큰에 박힌 값은 쓰일 자리가 없다.
 */
data class RefreshTokenClaims(
    val userId: Long,
    val tokenId: String,
    val issuedAt: Instant,
    val expiresAt: Instant,
)
