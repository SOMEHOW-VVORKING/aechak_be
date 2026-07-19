package com.aechak.application.auth.usecase.command

import com.aechak.domain.user.social.enums.SocialProvider

/**
 * 소셜 로그인 입력 — 모든 채널(네이티브 SDK·애플 웹 SIWA·웹 서버 콜백)이 id_token으로 수렴한다.
 * 형식 검증은 실행 모듈(@Valid)에서 끝난 상태다.
 */
data class SocialLoginCommand(
    val provider: SocialProvider,
    val idToken: String,
    /** 애플 전용 — revoke용 refresh token 교환에 사용. 카카오는 null. */
    val authorizationCode: String? = null,
    /** 애플이 최초 인가 1회만 제공. 저장처는 Open Question — 현재 미사용. */
    val appleFullName: String? = null,
)
