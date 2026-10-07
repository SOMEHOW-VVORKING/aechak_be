package com.aechak.application.auth.usecase

interface LogoutUseCase {
    /** 해당 refresh 세션 폐기 — 멱등(식별 불가 토큰도 조용히 성공). */
    fun logout(refreshToken: String)

    /** 사용자의 모든 refresh 세션 폐기 */
    fun revokeAll(userId: Long)
}
