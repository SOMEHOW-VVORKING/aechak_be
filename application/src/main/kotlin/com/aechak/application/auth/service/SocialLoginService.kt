package com.aechak.application.auth.service

import com.aechak.application.auth.error.AuthErrorCode
import com.aechak.application.auth.port.SocialTokenVerifier
import com.aechak.application.auth.usecase.command.SocialLoginCommand
import com.aechak.application.auth.usecase.result.SocialLoginResult
import com.aechak.application.user.user.usecase.UserUseCase
import com.aechak.common.error.BusinessException
import com.aechak.domain.user.error.UserErrorCode
import com.aechak.domain.user.social.SocialIdentity
import com.aechak.domain.user.social.enums.SocialProvider
import com.aechak.domain.user.social.repository.SocialIdentityRepository
import com.aechak.domain.user.social.vo.ProviderUser
import com.aechak.domain.user.user.User
import com.aechak.domain.user.user.enums.UserStatus
import com.aechak.domain.user.user.repository.UserRepository
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** 소셜 로그인 흐름: id_token 검증 → 회원 조회/생성 → 자체 토큰 발급 */
@Service
class SocialLoginService(
    private val socialTokenVerifier: SocialTokenVerifier,
    private val socialIdentityRepository: SocialIdentityRepository,
    private val userRepository: UserRepository,
    private val userUseCase: UserUseCase,
    private val tokenService: TokenService,
    private val rejoinPolicy: RejoinPolicy,
) {
    fun login(command: SocialLoginCommand): SocialLoginResult {
        val providerUser = socialTokenVerifier.verify(command.provider, command.idToken)

        val identity = socialIdentityRepository.findByProviderAndProviderId(command.provider, providerUser.providerId)
        val (user, isNew) =
            if (identity != null) {
                identity.updateEmail(providerUser.email)
                resolveReturningUser(identity, command.provider, providerUser)
            } else {
                ResolvedUser(registerAndLink(command.provider, providerUser), isNew = true)
            }

        // TODO 애플 로그인은 authorizationCode를 제공자 refresh token으로 교환해 암호화하여 저장한다.
        // 교환에 실패해도 로그인은 허용하고 다음 로그인에서 다시 저장을 시도

        val tokens = tokenService.issue(user.id, user.role)
        return SocialLoginResult(tokens, user.status, isNew)
    }

    /** 기존 소셜 계정의 상태에 따라 로그인을 허용하거나 재가입을 진행 */
    private fun resolveReturningUser(
        identity: SocialIdentity,
        provider: SocialProvider,
        providerUser: ProviderUser,
    ): ResolvedUser {
        val user = identity.user
        return when (user.status) {
            UserStatus.SUSPENDED -> {
                throw BusinessException(AuthErrorCode.ACCOUNT_BLOCKED)
            }

            UserStatus.WITHDRAWN -> {
                requireRejoinAllowed(user)
                discardWithdrawnLink(identity)
                ResolvedUser(registerAndLink(provider, providerUser), isNew = true)
            }

            else -> {
                ResolvedUser(user, isNew = false)
            }
        }
    }

    /** 탈퇴 후 제한 기간이 지나지 않았으면 재가입을 막고, 언제부터 가능한지 알림 */
    private fun requireRejoinAllowed(withdrawnUser: User) {
        // WITHDRAWN 상태는 withdraw()에서 withdrawnAt과 함께 설정. 값이 없으면 잘못된 서버 상태.
        val withdrawnAt =
            checkNotNull(withdrawnUser.withdrawnAt) {
                "WITHDRAWN 계정에 withdrawnAt이 없습니다 (userId=${withdrawnUser.id})"
            }
        val allowedFrom = rejoinPolicy.allowedFrom(withdrawnAt)
        if (LocalDateTime.now() < allowedFrom) {
            throw BusinessException(
                AuthErrorCode.REJOIN_BLOCKED,
                detail = "탈퇴한 계정입니다. ${allowedFrom.format(REJOIN_DATE_FORMAT)}부터 다시 가입할 수 있습니다.",
            )
        }
    }

    /**
     * 옛 연결을 지우고 바로 flush 한다.
     * Hibernate는 INSERT를 DELETE보다 먼저 내보내므로, 그냥 두면 새 연결 INSERT가
     * 아직 남아 있는 옛 행과 UNIQUE(provider, provider_id)로 충돌한다.
     */
    private fun discardWithdrawnLink(identity: SocialIdentity) {
        socialIdentityRepository.delete(identity)
        socialIdentityRepository.flush()
    }

    private fun registerAndLink(
        provider: SocialProvider,
        providerUser: ProviderUser,
    ): User {
        val user = registerUser()
        socialIdentityRepository.save(SocialIdentity.link(user, provider, providerUser.providerId, providerUser.email))
        return user
    }

    companion object {
        private val REJOIN_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy년 M월 d일")
    }

    /** 분기가 정한 로그인 대상과 이번 로그인이 신규 가입이었는지 */
    private data class ResolvedUser(
        val user: User,
        val isNew: Boolean,
    )

    /** 온보딩 대기 계정을 생성, 소셜 연결에 사용할 엔티티를 조회 */
    private fun registerUser(): User {
        val userId = userUseCase.registerFromSocial()
        return userRepository.findById(userId)
            ?: throw BusinessException(UserErrorCode.USER_NOT_FOUND)
    }
}
