package com.aechak.application.user.user.facade

import com.aechak.application.file.usecase.FileUseCase
import com.aechak.application.user.term.service.ConsentService
import com.aechak.application.user.user.service.UserService
import com.aechak.application.user.user.usecase.UserUseCase
import com.aechak.application.user.user.usecase.command.SetNicknameCommand
import com.aechak.application.user.user.usecase.query.UserSearchQuery
import com.aechak.application.user.user.usecase.result.UserMeResult
import com.aechak.application.user.user.usecase.result.UserSummaryResult
import com.aechak.common.error.BusinessException
import com.aechak.domain.user.error.UserErrorCode
import com.aechak.domain.user.user.User
import com.aechak.domain.user.user.UserProfile
import com.aechak.domain.user.user.enums.UserStatus
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate

/**
 * UserUseCase의 유일한 구현체 — 각 도메인이 따라갈 Facade 템플릿.
 *
 * - UseCase 구현은 항상 Facade다. Service가 UseCase를 직접 구현하지 않는다.
 * - @Transactional 경계는 여기 고정 — Service/도메인 메서드에는 붙이지 않는다. 조회는 readOnly = true.
 * - 도메인이 수집한 이벤트(aggregate.events)는 커밋 전 발행하고 clearEvents() 한다.
 * - 타 도메인 협력이 필요하면 그쪽 UseCase를 주입받는다(→ FileUseCase). 순환 의존이 생기면 이벤트 전환을 검토한다.
 *
 * setNickname만 @Transactional 대신 TransactionTemplate(프로그램적 경계)을 쓴다:
 * 닉네임 UNIQUE 위반은 커밋 시점 flush에서 터져 선언적 경계 안의 catch로는 잡을 수 없다 —
 * execute 밖 캐치에서 제약명을 보고 30002/멱등으로 번역한다(AuthFacade 선례 — 거긴 재시도, 여긴 번역).
 */
@Service
class UserFacade(
    private val userService: UserService,
    private val consentService: ConsentService,
    private val fileUseCase: FileUseCase,
    transactionManager: PlatformTransactionManager,
) : UserUseCase {
    private val tx = TransactionTemplate(transactionManager)

    @Transactional(readOnly = true)
    override fun checkNickname(
        userId: Long,
        nickname: String,
    ): Boolean = userService.isNicknameAvailable(nickname, excludeUserId = userId) // 본인 현재 닉네임은 사용 가능

    override fun setNickname(command: SetNicknameCommand): UserMeResult {
        try {
            tx.execute {
                val user = userService.getById(command.userId)
                when (user.status) {
                    UserStatus.PENDING_ONBOARDING -> completeOnboarding(user, command.nickname)

                    UserStatus.ACTIVE -> renameNickname(user, command.nickname)

                    // UserStatusFilter(20006)가 걸렀어야 할 상태 — 도달 자체가 방어선 이상이라 500이 맞다
                    else -> error("차단됐어야 할 상태의 닉네임 변경 시도 (userId=${user.id}, status=${user.status})")
                }
            } // ← 커밋까지 이 블록 안 — 커밋 시점 UNIQUE 폭발도 아래 catch가 잡는다
        } catch (e: DataIntegrityViolationException) {
            translateNicknameConflict(e)
        }
        return loadMe(command.userId)
    }

    /** 커밋 시점 무결성 위반의 번역 — 제약명 기반 분기(일괄 30002 번역은 오라벨). */
    private fun translateNicknameConflict(e: DataIntegrityViolationException) {
        val cause = e.mostSpecificCause.message.orEmpty()
        when {
            // check 통과 후 race 포함 — DB UNIQUE가 최종 판정
            UserProfile.UK_NICKNAME in cause -> throw BusinessException(UserErrorCode.DUPLICATE_NICKNAME, e)

            // 더블탭 동시 2건 — 프로필 PK(user_id) 충돌. 선행 커밋 결과를 그대로 응답(멱등)
            UserProfile.PK_CONFLICT_MARKER in cause -> Unit

            // 그 외 무결성 위반을 30002로 오번역하지 않는다
            else -> throw e
        }
    }

    /** 온보딩 완료 경로 — 동의 게이트(30009 판정은 ConsentService 소관) 통과 시에만 프로필 생성+ACTIVE 전이. */
    private fun completeOnboarding(
        user: User,
        nickname: String,
    ) {
        consentService.verifyRequiredConsents(user.id)
        user.completeOnboarding(nickname)
    }

    /** ACTIVE 재변경 경로 — 전이·동의 검증 없음, 같은 값이면 멱등. 프로필 수정 화면의 닉네임 저장도 이 경로다. */
    private fun renameNickname(
        user: User,
        nickname: String,
    ) {
        user.profile.rename(nickname)
    }

    @Transactional(readOnly = true)
    override fun getMe(userId: Long): UserMeResult = loadMe(userId)

    /** 프로필 노출은 ACTIVE에서만 — 그 외 상태는 프로필 계열 null(ACTIVE 아닌 조회는 어드민 모듈 몫), FE 재시작 라우팅은 status만 쓴다. */
    private fun loadMe(userId: Long): UserMeResult {
        val user = userService.getById(userId)
        val profile = if (user.status != UserStatus.ACTIVE) null else user.profile
        return UserMeResult(
            status = user.status,
            nickname = profile?.nickname,
            profileImageUrl = fileUseCase.resolveMediaUrl(profile?.profileImageKey),
            bio = profile?.bio,
            email = userService.findEmail(userId),
            isPhoneVerified = user.isPhoneVerified,
        )
    }

    @Transactional(readOnly = true)
    override fun searchUsers(query: UserSearchQuery): List<UserSummaryResult> {
        // TODO: 필터·페이징 조회 — 포트에 검색 메서드 추가 시 구현 (복잡 조회 전략은 CQRS-lite 논의)
        TODO("골격 템플릿 — 기능 구현 시 채운다")
    }

    @Transactional
    override fun registerFromSocial(): Long = userService.registerFromSocial().id
}
