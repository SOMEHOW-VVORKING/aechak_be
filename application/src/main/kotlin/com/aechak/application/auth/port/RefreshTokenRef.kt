package com.aechak.application.auth.port

import java.time.Instant

/** 회전 후속 토큰의 클레임 스냅샷 — 유예 중 재제시에 동일 토큰을 재생성(멱등)하기 위해 보관한다. */
data class RefreshTokenRef(
    val tokenId: String,
    val issuedAt: Instant,
    val expiresAt: Instant,
)
