package com.aechak.application.auth.usecase

import com.aechak.application.auth.usecase.command.SocialLoginCommand
import com.aechak.application.auth.usecase.result.SocialLoginResult

/**
 * auth 도메인 진입점 계약 — user와 달리 유스케이스별 인터페이스로 분리한다.
 * 구현은 AuthFacade 하나뿐이라는 규칙은 동일하다.
 */
interface SocialLoginUseCase {
    fun login(command: SocialLoginCommand): SocialLoginResult
}
