package com.aechak.application.auth.facade

import com.aechak.application.auth.service.SocialLoginService
import com.aechak.application.auth.service.TokenService
import com.aechak.application.auth.usecase.LogoutUseCase
import com.aechak.application.auth.usecase.SocialLoginUseCase
import com.aechak.application.auth.usecase.TokenRefreshUseCase
import com.aechak.application.auth.usecase.command.SocialLoginCommand
import com.aechak.application.auth.usecase.result.SocialLoginResult
import com.aechak.application.auth.usecase.result.TokenResult
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

/**
 * auth 유스케이스들의 유일한 구현 Facade — 트랜잭션 경계 소유는 Facade 고정 규칙 그대로.
 *
 * login만 @Transactional 대신 TransactionTemplate(프로그램적 경계)을 쓴다:
 * 동시 가입 race의 재조회 폴백은 "실패(롤백)한 트랜잭션 밖에서 새 트랜잭션으로 재시도"가 필요해
 * 선언적 경계로는 표현할 수 없다. refresh/logout은 Redis만 만지므로 DB 트랜잭션이 없다.
 */
@Service
class AuthFacade(
    private val socialLoginService: SocialLoginService,
    private val tokenService: TokenService,
    transactionManager: PlatformTransactionManager,
) : SocialLoginUseCase, TokenRefreshUseCase, LogoutUseCase {

    private val loginTx = TransactionTemplate(transactionManager)

    override fun login(command: SocialLoginCommand): SocialLoginResult =
        try {
            loginTx.execute { socialLoginService.login(command) }!!
        } catch (e: DataIntegrityViolationException) {
            // 동시 가입 race — 승자의 identity를 새 트랜잭션에서 재조회(폴백)
            loginTx.execute { socialLoginService.login(command) }!!
        }

    override fun refresh(refreshToken: String): TokenResult = tokenService.rotate(refreshToken)

    override fun logout(refreshToken: String) = tokenService.revoke(refreshToken)
}
