package com.aechak.application.auth.service

import com.aechak.application.auth.error.AuthErrorCode
import com.aechak.application.auth.port.PasswordHasher
import com.aechak.application.auth.usecase.command.AdminPasswordLoginCommand
import com.aechak.application.auth.usecase.result.TokenResult
import com.aechak.common.error.BusinessException
import com.aechak.domain.user.credential.repository.AdminCredentialRepository
import com.aechak.domain.user.user.User
import com.aechak.domain.user.user.enums.UserStatus
import org.springframework.stereotype.Service

/** 아이디·비밀번호 로그인 흐름: 자격 조회 → 해시 대조 → 계정 상태 확인 → 자체 토큰 발급 */
@Service
class AdminPasswordLoginService(
    private val adminCredentialRepository: AdminCredentialRepository,
    private val passwordHasher: PasswordHasher,
    private val tokenService: TokenService,
) {
    fun login(command: AdminPasswordLoginCommand): TokenResult {
        val credential = adminCredentialRepository.findByLoginId(command.loginId)

        // 아이디가 없는 경우와 비밀번호가 틀린 경우를 같은 예외로 합친다. 가르면 그 차이가
        // "이 아이디는 존재한다"는 신호가 되어 공격자가 아이디 목록을 먼저 모을 수 있다.
        if (credential == null || !passwordHasher.matches(command.password, credential.passwordHash)) {
            throw BusinessException(AuthErrorCode.INVALID_CREDENTIALS)
        }

        val user = credential.user
        requireLoginable(user)
        return tokenService.issue(user.id, user.role)
    }

    /**
     * 소셜 로그인과 같은 기준으로 막는다 — 저장값이 아니라 유효 상태로 보므로 기간이 지난 임시 정지는
     * 통과하고, 이용 제한(RESTRICTED)은 로그인을 막는 제재가 아니라 통과한다.
     *
     * 탈퇴 계정은 소셜과 다르게 다룬다. 소셜은 재가입으로 이어지지만 어드민 자격은 운영자가 발급하는
     * 것이라 본인 의사로 되살릴 수 있는 것이 아니다 — 차단하고 운영자가 다시 발급하게 한다.
     */
    private fun requireLoginable(user: User) {
        when (user.effectiveStatus()) {
            UserStatus.SUSPENDED, UserStatus.BANNED, UserStatus.WITHDRAWN -> {
                throw BusinessException(AuthErrorCode.ACCOUNT_BLOCKED)
            }

            else -> {
                Unit
            }
        }
    }
}
