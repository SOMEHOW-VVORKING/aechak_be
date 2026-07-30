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

/**
 * 소셜 로그인 흐름: id_token 검증 → 회원 조회/생성 → 자체 토큰 발급.
 *
 * - 회원 생성은 UserUseCase에 위임(auth→user 단방향). 연관 매핑용 엔티티 로드만
 *   UserRepository 포트를 직접 쓴다(같은 트랜잭션·영속성 컨텍스트라 추가 비용 없음).
 * - SocialIdentity는 domain.user.social 소속이지만 유스케이스 소유는 auth다
 *   (domain 패키징과 application 패키징은 1:1일 필요가 없다).
 */
@Service
class SocialLoginService(
    private val socialTokenVerifier: SocialTokenVerifier,
    private val socialIdentityRepository: SocialIdentityRepository,
    private val userRepository: UserRepository,
    private val userUseCase: UserUseCase,
    private val tokenService: TokenService,
) {
    fun login(command: SocialLoginCommand): SocialLoginResult {
        val providerUser = socialTokenVerifier.verify(command.provider, command.idToken)

        val identity = socialIdentityRepository.findByProviderAndProviderId(command.provider, providerUser.providerId)
        val (user, isNew) =
            if (identity != null) {
                identity.updateEmail(providerUser.email)
                identity.user to false
            } else {
                registerAndLink(command.provider, providerUser) to true
            }

        // WITHDRAWN 재로그인(재가입) 정책은 user 도메인 미결 — 확정 전까지 차단한다.
        if (user.status == UserStatus.SUSPENDED || user.status == UserStatus.WITHDRAWN) {
            throw BusinessException(AuthErrorCode.ACCOUNT_BLOCKED)
        }

        // TODO: 애플이면 authorizationCode 교환 → provider refresh token AES-256 저장.
        //           교환 실패는 완충(로그인 통과 + 경고 로그) — 다음 로그인 upsert로 자기치유.

        val tokens = tokenService.issue(user.id, user.role)
        return SocialLoginResult(tokens, user.status, isNew)
    }

    private fun registerAndLink(
        provider: SocialProvider,
        providerUser: ProviderUser,
    ): User {
        val userId = userUseCase.registerFromSocial()
        val user =
            userRepository.findById(userId)
                ?: throw BusinessException(UserErrorCode.USER_NOT_FOUND)
        // 동시 가입 race: UNIQUE(provider, provider_id) 충돌은 여기서 터진다 —
        // 트랜잭션이 통째로 롤백되고(고아 user 없음) AuthFacade가 새 트랜잭션으로 재시도해 승자를 조회한다.
        socialIdentityRepository.save(SocialIdentity.link(user, provider, providerUser.providerId, providerUser.email))
        return user
    }
}
