package com.aechak.application.auth.usecase

import com.aechak.application.auth.usecase.command.AdminPasswordLoginCommand
import com.aechak.application.auth.usecase.result.TokenResult

/**
 * 아이디·비밀번호 로그인 계약. 소셜 로그인과 병존하고 어느 쪽으로 들어와도 발급되는 토큰은 같다.
 *
 * ADMIN 자격을 여기서 보지 않는다 — 소셜 로그인도 역할을 보지 않고 발급하며, 권한 판정은
 * 어드민 모듈의 게이트가 한다. 자격 행이 있어도 users.role이 GENERAL이면 로그인은 되고
 * 어드민 API 호출이 403으로 막힌다.
 */
interface AdminPasswordLoginUseCase {
    fun loginWithPassword(command: AdminPasswordLoginCommand): TokenResult
}
