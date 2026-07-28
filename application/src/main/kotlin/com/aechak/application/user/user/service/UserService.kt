package com.aechak.application.user.user.service

import com.aechak.common.error.BusinessException
import com.aechak.domain.user.error.UserErrorCode
import com.aechak.domain.user.social.repository.SocialIdentityRepository
import com.aechak.domain.user.user.User
import com.aechak.domain.user.user.repository.UserRepository
import org.springframework.stereotype.Service

/**
 * user 도메인 비즈니스 로직 보관함 — 각 도메인이 따라갈 Service 템플릿.
 * 인터페이스 없이 Facade에서만 호출된다 — Controller/Consumer/타 도메인이 직접 호출하지 않는다.
 * 리포지토리는 domain의 포트를 주입받는다 (구현은 infra 어댑터).
 */
@Service
class UserService(
    private val userRepository: UserRepository,
    private val socialIdentityRepository: SocialIdentityRepository,
) {
    /** 조회 실패는 BusinessException + 도메인 ErrorCode로 던진다. */
    fun getById(userId: Long): User =
        userRepository.findById(userId)
            ?: throw BusinessException(UserErrorCode.USER_NOT_FOUND)

    /** 소셜 가입 — 프로필 없는 PENDING_ONBOARDING 계정 생성. */
    fun registerFromSocial(): User = userRepository.save(User.preRegister())

    /** 닉네임 선점 여부 — 본인 제외, 비교는 컬럼 collation(ci) 기준. */
    fun isNicknameTaken(
        nickname: String,
        excludeUserId: Long,
    ): Boolean = userRepository.isNicknameTaken(nickname, excludeUserId)

    /** 내 정보의 소셜 이메일 — 연결 없음·미제공(애플 가림)이면 null. */
    fun findEmail(userId: Long): String? = socialIdentityRepository.findByUserId(userId)?.email
}
