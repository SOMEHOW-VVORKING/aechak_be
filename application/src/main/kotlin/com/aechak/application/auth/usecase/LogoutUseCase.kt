package com.aechak.application.auth.usecase

interface LogoutUseCase {
    /** 해당 refresh 세션 폐기 — 멱등(식별 불가 토큰도 조용히 성공). */
    fun logout(refreshToken: String)
}
