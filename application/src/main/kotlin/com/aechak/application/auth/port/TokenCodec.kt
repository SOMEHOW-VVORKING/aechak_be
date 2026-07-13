package com.aechak.application.auth.port

import java.time.Instant

/** 검증을 통과한 자체 refresh token의 클레임. */
data class RefreshTokenClaims(
    val userId: Long,
    val role: String,
    val tokenId: String,
    val issuedAt: Instant,
    val expiresAt: Instant,
)

/**
 * 자체 JWT 인코딩/디코딩 아웃바운드 포트 — 구현은 boot:api(Nimbus 어댑터).
 * 이 포트를 둔 이유: TokenService의 회전·재사용 감지 로직을
 * Spring Security 타입 없이 테스트하기 위한 격리 지점(Spring 타입 역류 금지 규칙).
 *
 * 멱등 재발급 전제: RS256 서명은 결정적이므로 동일 클레임(초 단위 시각) 인코딩 = 동일 토큰 문자열.
 */
interface TokenCodec {
    fun encodeAccessToken(userId: Long, role: String, issuedAt: Instant, expiresAt: Instant): String

    fun encodeRefreshToken(userId: Long, role: String, tokenId: String, issuedAt: Instant, expiresAt: Instant): String

    /** 서명·만료·token_type=refresh 검증 실패 시 null. */
    fun decodeRefreshToken(token: String): RefreshTokenClaims?
}
