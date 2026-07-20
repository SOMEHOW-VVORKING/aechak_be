package com.aechak.application.auth.usecase

import com.aechak.application.auth.usecase.result.WebLoginCompletion
import com.aechak.application.auth.usecase.result.WebLoginPreparation
import com.aechak.domain.user.social.enums.SocialProvider

/**
 * 웹(브라우저) 채널의 서버 콜백 로그인 진입점.
 * 브라우저 고유물(쿠키·302 조립)은 컨트롤러 몫이고, 여기는 검증·state·로그인 판정까지만 담당한다.
 */
interface WebLoginUseCase {
    /**
     * 로그인 진입 준비: returnUrl 화이트리스트 검증(실패 시 400 — 신뢰 불가 주소로는 리다이렉트 금지)
     * → state 발급·저장 → authorize URL과 state 반환(state는 컨트롤러의 브라우저 바인딩 쿠키용).
     */
    fun prepareLogin(
        provider: SocialProvider,
        returnUrl: String,
    ): WebLoginPreparation

    /**
     * provider 콜백 처리: state 1회 소비(실패 시 예외 — returnUrl을 모르므로 리다이렉트 불가)
     * → code 교환·로그인. 로그인 실패는 예외가 아니라 Failure로 — 컨트롤러가 return으로 실어 보낸다.
     * code가 null이면 provider 측 거부(사용자 취소 등)로 취급한다.
     */
    fun completeLogin(
        provider: SocialProvider,
        code: String?,
        state: String,
    ): WebLoginCompletion
}
