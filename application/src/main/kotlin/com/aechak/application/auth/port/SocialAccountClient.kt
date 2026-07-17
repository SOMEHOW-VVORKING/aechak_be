package com.aechak.application.auth.port

/**
 * 소셜 계정 원격 조작 아웃바운드 포트 — 구현은 infra:social-client (T6/T10에서 어댑터 작성).
 * 외부사 장애는 어댑터가 BusinessException으로 번역하되, code 교환 실패는 null 반환(완충 — 호출부가 경고 로그).
 */
interface SocialAccountClient {
    /** 애플 authorizationCode → provider refresh token(AES-256 암호문). 실패 시 null. */
    fun exchangeAppleCode(authorizationCode: String): ByteArray?

    /** 탈퇴 시 애플 토큰 revoke — 애플 정책상 의무. */
    fun revokeApple(refreshTokenEnc: ByteArray)

    /** 탈퇴 시 카카오 연결 해제 — admin key 기반이라 저장 토큰 불필요. */
    fun unlinkKakao(providerId: String)
}
