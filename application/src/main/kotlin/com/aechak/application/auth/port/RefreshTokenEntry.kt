package com.aechak.application.auth.port

/** 저장소에 남아 있는 refresh token 엔트리 — 현행(Active) 또는 회전 유예(Rotated) 두 상태뿐이다. */
sealed interface RefreshTokenEntry {
    /** 현행 토큰 — 제시 토큰의 SHA-256 해시와 대조한다. */
    data class Active(
        val tokenHash: String,
    ) : RefreshTokenEntry

    /** 회전으로 강등된 토큰 — 유예(grace) 동안만 남아 successor 멱등 재발급에 쓰인다. */
    data class Rotated(
        val successor: RefreshTokenRef,
    ) : RefreshTokenEntry
}
