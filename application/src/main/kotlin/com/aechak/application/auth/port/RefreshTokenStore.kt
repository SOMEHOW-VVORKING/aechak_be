package com.aechak.application.auth.port

import java.time.Duration
import java.time.Instant

/** 회전 후속 토큰의 클레임 스냅샷 — 유예 중 재제시에 동일 토큰을 재생성(멱등)하기 위해 보관한다. */
data class RefreshTokenRef(
    val tokenId: String,
    val issuedAt: Instant,
    val expiresAt: Instant,
)

sealed interface RefreshTokenEntry {
    /** 현행 토큰 — 제시 토큰의 SHA-256 해시와 대조한다. */
    data class Active(val tokenHash: String) : RefreshTokenEntry

    /** 회전으로 강등된 토큰 — 유예(grace) 동안만 남아 successor 멱등 재발급에 쓰인다. */
    data class Rotated(val successor: RefreshTokenRef) : RefreshTokenEntry
}

/**
 * 자체 refresh token 저장소 아웃바운드 포트 — 구현은 infra:redis (TTL은 저장소에 위임).
 * 포트인 이유(팀 원칙): 회전·유예·재사용 감지라는 유스케이스 로직이 테스트할 가치가 있다.
 */
interface RefreshTokenStore {
    fun save(userId: Long, tokenId: String, tokenHash: String, ttl: Duration)
    fun find(userId: Long, tokenId: String): RefreshTokenEntry?

    /** 회전: 옛 엔트리를 삭제 대신 successor 기록 + 유예 TTL로 강등한다. */
    fun markRotated(userId: Long, tokenId: String, successor: RefreshTokenRef, grace: Duration)

    fun delete(userId: Long, tokenId: String)

    /** 전 세션 무효화 — 재사용(탈취) 감지·탈퇴·제재 시. */
    fun deleteAll(userId: Long)
}
