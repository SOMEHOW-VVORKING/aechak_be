package com.aechak.application.auth.port

import com.aechak.domain.user.social.enums.SocialProvider

/**
 * 웹(JS SDK) 채널 로그인의 authorization code → id_token 교환 아웃바운드 포트 — 구현은 infra:social-client.
 *
 * - 교환은 OAuth 표준이라 provider 차이는 설정(token-uri·client-id)뿐 — 시그니처는 provider 일반화.
 * - 네이티브 SDK 채널은 id_token을 직접 제출하므로 이 포트를 타지 않는다.
 * - 애플 웹(SIWA JS)은 form_post 응답에 id_token이 동봉되므로 교환 불필요 — 현재 카카오만 활성.
 * - 교환 실패(만료·위조 code, redirect_uri 불일치)는 BusinessException(INVALID_SOCIAL_TOKEN),
 *   교환 미설정 provider는 UNSUPPORTED_PROVIDER로 번역한다.
 */
interface AuthorizationCodeExchanger {
    /** @return provider가 발급한 id_token — 이후 검증 파이프라인은 네이티브 채널과 동일. */
    fun exchange(
        provider: SocialProvider,
        code: String,
        redirectUri: String,
    ): String
}
