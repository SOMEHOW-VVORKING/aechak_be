package com.aechak.application.user.user.facade

import com.aechak.application.file.port.enums.UploadPurpose
import com.aechak.application.file.usecase.FileUseCase
import com.aechak.application.file.usecase.command.PromoteFileCommand
import com.aechak.application.pii.port.PiiCrypto
import com.aechak.application.user.term.service.ConsentService
import com.aechak.application.user.user.service.UserService
import com.aechak.application.user.user.support.PhoneNumberMasker
import com.aechak.application.user.user.usecase.UserUseCase
import com.aechak.application.user.user.usecase.command.SetNicknameCommand
import com.aechak.application.user.user.usecase.command.UpdateProfileCommand
import com.aechak.application.user.user.usecase.query.UserSearchQuery
import com.aechak.application.user.user.usecase.result.UserAuthorResult
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
 * setNickname·updateProfile은 @Transactional 대신 TransactionTemplate(프로그램적 경계)을 쓴다:
 * 닉네임 UNIQUE 위반은 커밋 시점 flush에서 터져 선언적 경계 안의 catch로는 잡을 수 없다 —
 * execute 밖 캐치에서 제약명을 보고 30001/멱등으로 번역한다(AuthFacade 선례 — 거긴 재시도, 여긴 번역).
 * updateProfile은 추가로 승격(S3 외부 호출)을 트랜잭션 밖에 두기 위해서이기도 하다.
 */
@Service
class UserFacade(
    private val userService: UserService,
    private val consentService: ConsentService,
    private val fileUseCase: FileUseCase,
    private val piiCrypto: PiiCrypto,
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

                    // 온보딩 전용 EP — ACTIVE의 닉네임 변경은 updateProfile이 유일 경로다
                    UserStatus.ACTIVE -> throw BusinessException(UserErrorCode.ONBOARDING_ALREADY_COMPLETED)

                    // UserStatusFilter(20005)가 걸렀어야 할 상태 — 도달 자체가 방어선 이상이라 500이 맞다
                    else -> error("차단됐어야 할 상태의 닉네임 변경 시도 (userId=${user.id}, status=${user.status})")
                }
            } // ← 커밋까지 이 블록 안 — 커밋 시점 UNIQUE 폭발도 아래 catch가 잡는다
        } catch (e: DataIntegrityViolationException) {
            translateNicknameConflict(e)
        }
        return loadMe(command.userId)
    }

    /** 커밋 시점 무결성 위반의 번역 — 제약명 기반 분기(일괄 30001 번역은 오라벨). */
    private fun translateNicknameConflict(e: DataIntegrityViolationException) {
        val cause = e.mostSpecificCause.message.orEmpty()
        when {
            // check 통과 후 race 포함 — DB UNIQUE가 최종 판정
            UserProfile.UK_NICKNAME in cause -> throw BusinessException(UserErrorCode.DUPLICATE_NICKNAME, e)

            // 더블탭 동시 2건 — 프로필 PK(user_id) 충돌. 선행 커밋 결과를 그대로 응답(멱등)
            UserProfile.PK_CONFLICT_MARKER in cause -> Unit

            // 그 외 무결성 위반을 30001로 오번역하지 않는다
            else -> throw e
        }
    }

    /** 온보딩 완료 경로 — 동의 게이트(30300 판정은 ConsentService 소관) 통과 시에만 프로필 생성+ACTIVE 전이. */
    private fun completeOnboarding(
        user: User,
        nickname: String,
    ) {
        consentService.verifyRequiredConsents(user.id)
        user.completeOnboarding(nickname)
    }

    /**
     * 프로필 전체 교체 — 승격(S3 외부 호출)은 트랜잭션 밖에서 먼저 하고 저장은 짧은 tx로 커밋한다
     * (DB 커넥션을 잡은 채 외부 I/O를 기다리지 않는다). 실패 잔여물(교체된 옛 key·승격 후 커밋 실패분)은
     * S3 고아로 남는다 — MVP 수용, 정리 배치는 후속.
     */
    override fun updateProfile(command: UpdateProfileCommand): UserMeResult {
        val currentKey = userService.getById(command.userId).profile.profileImageKey
        // null=제거 / 저장값과 같으면 유지(저장값 대조가 곧 소유 증명) / 그 외는 전부 새 업로드로 보고 승격
        val finalKey =
            command.profileImageKey?.let {
                if (it == currentKey) it else promoteImage(command.userId, it)
            }
        try {
            tx.execute {
                userService.getById(command.userId).updateProfile(command.nickname, command.bio, finalKey)
            }
        } catch (e: DataIntegrityViolationException) {
            translateNicknameConflict(e)
        }
        return loadMe(command.userId)
    }

    private fun promoteImage(
        userId: Long,
        tmpKey: String,
    ): String = fileUseCase.promote(PromoteFileCommand(tmpKey, userId, UploadPurpose.USER_PROFILE)).key

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
            profileImageKey = profile?.profileImageKey,
            bio = profile?.bio,
            email = userService.findEmail(userId),
            // 원문은 반출 금지 — 복호 후 즉시 마스킹(가운데 자리 전체)해 표시용으로만 내린다
            phoneNumber = user.phoneNumber?.let { PhoneNumberMasker.mask(piiCrypto.decrypt(it)) },
            isPhoneVerified = user.isPhoneVerified,
        )
    }

    @Transactional(readOnly = true)
    override fun searchUsers(query: UserSearchQuery): List<UserSummaryResult> {
        // TODO: 필터·페이징 조회 — 포트에 검색 메서드 추가 시 구현 (복잡 조회 전략은 CQRS-lite 논의)
        TODO("골격 템플릿 — 기능 구현 시 채운다")
    }

    @Transactional(readOnly = true)
    override fun getAuthors(userIds: Collection<Long>): Map<Long, UserAuthorResult> {
        val found = userService.getAuthors(userIds).associateBy { it.id }
        return userIds.toSet().associateWith { id ->
            val author = found[id]
            val withdrawn = author?.status == UserStatus.WITHDRAWN
            UserAuthorResult(
                userId = id,
                nickname = if (withdrawn) ANONYMIZED_NICKNAME else (author?.nickname ?: ANONYMIZED_NICKNAME),
                withdrawn = withdrawn,
                profileImageUrl = if (withdrawn) null else fileUseCase.resolveMediaUrl(author?.profileImageKey),
            )
        }
    }

    @Transactional
    override fun registerFromSocial(): Long = userService.registerFromSocial().id

    companion object {
        private const val ANONYMIZED_NICKNAME = "탈퇴한 사용자"
    }
}
