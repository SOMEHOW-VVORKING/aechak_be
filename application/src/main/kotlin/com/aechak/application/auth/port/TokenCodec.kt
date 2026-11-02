package com.aechak.application.auth.port

import java.time.Instant

/**
 * 자체 JWT 인코딩/디코딩 아웃바운드 포트 — 구현은 boot:api(Nimbus 어댑터).
 * 이 포트를 둔 이유: TokenService의 회전·재사용 감지 로직을
 * Spring Security 타입 없이 테스트하기 위한 격리 지점(Spring 타입 역류 금지 규칙).
 *
 * 멱등 재발급 전제: RS256 서명은 결정적이므로 동일 클레임(초 단위 시각) 인코딩 = 동일 토큰 문자열.
 *
 * refresh에는 역할을 싣지 않는다. 권한 판정은 access가 하고 회전은 역할을 DB에서 다시 읽으므로
 * refresh의 역할은 쓰이지 않는데, 서명 입력에 들어가면 토큰 바이트만 흔들어 유예 재발급의
 * 재현을 깨뜨린다(역할이 바뀐 직후 재시도가 해시 대조에서 탈취로 오판된다).
 */
interface TokenCodec {
    fun encodeAccessToken(
        userId: Long,
        role: String,
        issuedAt: Instant,
        expiresAt: Instant,
    ): String

    fun encodeRefreshToken(
        userId: Long,
        tokenId: String,
        issuedAt: Instant,
        expiresAt: Instant,
    ): String

    /** 서명·만료·token_type=refresh 검증 실패 시 null. */
    fun decodeRefreshToken(token: String): RefreshTokenClaims?
}
