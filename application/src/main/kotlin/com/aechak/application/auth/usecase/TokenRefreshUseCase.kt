package com.aechak.application.auth.usecase

import com.aechak.application.auth.usecase.result.TokenResult

interface TokenRefreshUseCase {
    /** 회전(rotation): 새 쌍 발급 + 옛 토큰 유예 강등. 재사용 감지 시 전 세션 무효화 후 20003. */
    fun refresh(refreshToken: String): TokenResult
}
