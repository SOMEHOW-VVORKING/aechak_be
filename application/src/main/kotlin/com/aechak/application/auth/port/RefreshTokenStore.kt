package com.aechak.application.auth.port

import java.time.Duration

/**
 * 자체 refresh token 저장소 아웃바운드 포트 — 구현은 infra:redis (TTL은 저장소에 위임).
 * 포트인 이유(팀 원칙): 회전·유예·재사용 감지라는 유스케이스 로직이 테스트할 가치가 있다.
 */
interface RefreshTokenStore {
    fun save(
        userId: Long,
        tokenId: String,
        tokenHash: String,
        ttl: Duration,
    )

    fun find(
        userId: Long,
        tokenId: String,
    ): RefreshTokenEntry?

    /** 회전: 옛 엔트리를 삭제 대신 successor 기록 + 유예 TTL로 강등한다. */
    fun markRotated(
        userId: Long,
        tokenId: String,
        successor: RefreshTokenRef,
        grace: Duration,
    )

    fun delete(
        userId: Long,
        tokenId: String,
    )

    /** 전 세션 무효화 — 재사용(탈취) 감지·탈퇴·제재 시. */
    fun deleteAll(userId: Long)
}
