package com.aechak.application.auth.facade

import com.aechak.application.auth.error.AuthErrorCode
import com.aechak.application.auth.service.SocialLoginService
import com.aechak.application.auth.service.TokenService
import com.aechak.application.auth.service.WebLoginService
import com.aechak.application.auth.usecase.LogoutUseCase
import com.aechak.application.auth.usecase.SocialLoginUseCase
import com.aechak.application.auth.usecase.TokenRefreshUseCase
import com.aechak.application.auth.usecase.WebLoginUseCase
import com.aechak.application.auth.usecase.command.SocialLoginCommand
import com.aechak.application.auth.usecase.result.SocialLoginResult
import com.aechak.application.auth.usecase.result.TokenResult
import com.aechak.application.auth.usecase.result.WebLoginCompletion
import com.aechak.application.auth.usecase.result.WebLoginPreparation
import com.aechak.common.error.BusinessException
import com.aechak.domain.user.social.enums.SocialProvider
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

/**
 * 로그인은 동시 가입 충돌로 트랜잭션이 롤백된 뒤 새 트랜잭션에서 다시 조회해야 하므로
 * TransactionTemplate을, 토큰 갱신과 로그아웃은 Redis 사용
 */
@Service
class AuthFacade(
    private val socialLoginService: SocialLoginService,
    private val webLoginService: WebLoginService,
    private val tokenService: TokenService,
    transactionManager: PlatformTransactionManager,
) : SocialLoginUseCase,
    TokenRefreshUseCase,
    LogoutUseCase,
    WebLoginUseCase {
    private val log = LoggerFactory.getLogger(javaClass)

    private val tx = TransactionTemplate(transactionManager)

    override fun login(command: SocialLoginCommand): SocialLoginResult = loginWithRetry(command)

    override fun refresh(refreshToken: String): TokenResult = tokenService.rotate(refreshToken)

    override fun logout(refreshToken: String) = tokenService.revoke(refreshToken)

    override fun revokeAll(userId: Long) = tokenService.revokeAll(userId)

    override fun prepareLogin(
        provider: SocialProvider,
        returnUrl: String,
    ): WebLoginPreparation = webLoginService.prepareLogin(provider, returnUrl)

    override fun completeLogin(
        provider: SocialProvider,
        code: String?,
        state: String,
    ): WebLoginCompletion {
        // state 소비 실패 = returnUrl을 모름 → 리다이렉트 불가, 유일하게 직접 400으로 끝나는 실패
        val returnUrl =
            webLoginService.consumeState(state)
                ?: throw BusinessException(AuthErrorCode.INVALID_LOGIN_STATE)

        if (code == null) {
            // provider 측 거부(사용자 취소 등) — code 없이 콜백만 옴
            log.warn("웹 로그인 콜백에 code 없음: provider={} (사용자 취소 또는 provider 거부)", provider)
            return WebLoginCompletion.Failure(returnUrl, AuthErrorCode.AUTHORIZATION_CODE_MISSING)
        }

        return try {
            val idToken = webLoginService.exchangeCode(provider, code)
            val login = loginWithRetry(SocialLoginCommand(provider = provider, idToken = idToken))
            WebLoginCompletion.Success(returnUrl, login.tokens.refreshToken, login.userStatus, login.isNew)
        } catch (e: BusinessException) {
            // 콜백 응답에는 body 수신자가 없다 — 실패도 (검증된) return으로 실어 보낸다.
            log.warn("웹 로그인 실패: provider={}, errorCode={}", provider, e.errorCode.code, e)
            WebLoginCompletion.Failure(returnUrl, e.errorCode)
        }
    }

    private fun loginWithRetry(command: SocialLoginCommand): SocialLoginResult =
        try {
            tx.execute { socialLoginService.login(command) }
        } catch (e: DataIntegrityViolationException) {
            // 가입 요청이 겹친 경우 먼저 생성된 소셜 연결을 새 트랜잭션에서 조회
            tx.execute { socialLoginService.login(command) }
        } catch (e: OptimisticLockingFailureException) {
            // 재가입이 겹쳐 옛 연결을 다른 요청이 먼저 지운 경우 새 트랜잭션에서 다시 조회
            tx.execute { socialLoginService.login(command) }
        }
}
