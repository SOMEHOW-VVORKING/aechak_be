package com.aechak.application.auth.usecase.command

import com.aechak.domain.user.social.enums.SocialProvider

/**
 * 채널별 입력(서버는 채널 중립):
 * - 네이티브 SDK / 애플 웹(SIWA JS form_post): idToken 제출
 * - 웹 JS SDK(카카오): code + redirectUri 제출 → 서버가 id_token으로 교환
 * 두 방식 중 정확히 하나 — 형식 검증은 실행 모듈(@Valid)에서 끝난 상태다.
 */
data class SocialLoginCommand(
    val provider: SocialProvider,
    val idToken: String? = null,
    /** 웹(JS SDK) 채널 — id_token 교환용 1회용 인가 코드. */
    val code: String? = null,
    /** code 발급에 사용한 redirect URI(FE 라우트) — 교환 요청에 동일 값이 필요하다. */
    val redirectUri: String? = null,
    /** 애플 전용 — revoke용 refresh token 교환에 사용. 카카오는 null. */
    val authorizationCode: String? = null,
    /** 애플이 최초 인가 1회만 제공. 저장처는 Open Question — 현재 미사용. */
    val appleFullName: String? = null,
)
