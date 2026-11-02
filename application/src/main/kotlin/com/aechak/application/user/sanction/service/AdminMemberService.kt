package com.aechak.application.user.sanction.service

import com.aechak.application.audit.support.AdminAuditContextHolder
import com.aechak.application.support.CursorPageSize
import com.aechak.application.user.sanction.port.AdminMemberQueryPort
import com.aechak.application.user.sanction.port.MemberPageCondition
import com.aechak.application.user.sanction.port.view.MemberSummaryView
import com.aechak.application.user.sanction.port.view.SanctionHistoryView
import com.aechak.application.user.sanction.support.MemberCursorCodec
import com.aechak.application.user.sanction.usecase.command.ReleaseSanctionCommand
import com.aechak.application.user.sanction.usecase.command.SanctionMemberCommand
import com.aechak.application.user.sanction.usecase.query.MemberSearchQuery
import com.aechak.common.error.BusinessException
import com.aechak.common.error.CommonErrorCode
import com.aechak.domain.user.error.UserErrorCode
import com.aechak.domain.user.sanction.UserSanction
import com.aechak.domain.user.sanction.enums.SanctionAction
import com.aechak.domain.user.sanction.repository.UserSanctionRepository
import com.aechak.domain.user.user.User
import com.aechak.domain.user.user.enums.UserRole
import com.aechak.domain.user.user.repository.UserRepository
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.LocalDateTime

/** 어드민 회원 관리 비즈니스 로직 보관함 — Facade에서만 호출된다. 전역 자원 접근이라 소유권 검증이 없다. */
@Service
class AdminMemberService(
    private val userRepository: UserRepository,
    private val userSanctionRepository: UserSanctionRepository,
    private val adminMemberQueryPort: AdminMemberQueryPort,
) {
    fun findMemberPage(query: MemberSearchQuery): List<MemberSummaryView> = adminMemberQueryPort.findMemberPage(toCondition(query))

    fun countMembers(query: MemberSearchQuery): Long = adminMemberQueryPort.countMembers(toCondition(query))

    fun findMember(userId: Long): MemberSummaryView =
        adminMemberQueryPort.findMember(userId) ?: throw BusinessException(UserErrorCode.USER_NOT_FOUND)

    fun findSanctionHistory(
        userId: Long,
        limit: Int,
    ): List<SanctionHistoryView> = adminMemberQueryPort.findSanctionHistory(userId, limit)

    /**
     * 제재 적용. 검증을 이력 행 생성(입력)과 상태 전이(도메인) 두 단계로 나눠 두고
     * 둘 다 통과한 뒤에만 저장한다 — 상태만 바뀌고 이력이 없는 행이 생기면 분쟁에서 쓸 수 없다.
     */
    fun sanction(command: SanctionMemberCommand) {
        // 해제는 사유 어휘와 상태 판정이 다른 별도 경로다 — 제재 경로로 들어오면 요청 자체가 잘못됐다
        if (command.action == SanctionAction.RELEASE) {
            throw BusinessException(CommonErrorCode.INVALID_REQUEST)
        }
        val target = loadSanctionTarget(command.adminId, command.targetUserId)
        val effectiveUntil = command.durationDays?.let { LocalDateTime.now().plusDays(it.toLong()) }
        val history =
            UserSanction.sanction(
                targetUser = target,
                action = command.action,
                reasonCode = command.reasonCode,
                reasonDetail = command.reasonDetail,
                effectiveUntil = effectiveUntil,
                memo = command.memo,
                actedByAdminId = command.adminId,
            )
        applyToAccount(target, command.action, effectiveUntil)
        userSanctionRepository.save(history)
    }

    /** 제재 해제 — 해제도 UPDATE가 아니라 이력 한 행으로 남긴다. */
    fun release(command: ReleaseSanctionCommand) {
        val target = loadSanctionTarget(command.adminId, command.targetUserId)
        val history =
            UserSanction.release(
                targetUser = target,
                reasonDetail = command.reasonDetail,
                memo = command.memo,
                actedByAdminId = command.adminId,
            )
        target.releaseSanction()
        userSanctionRepository.save(history)
    }

    private fun applyToAccount(
        target: User,
        action: SanctionAction,
        effectiveUntil: LocalDateTime?,
    ) {
        when (action) {
            SanctionAction.RESTRICT -> target.restrict()

            // 기간 누락은 이력 생성에서 이미 걸러졌다 — 여기 도달하면 값이 있다
            SanctionAction.SUSPEND -> target.suspendUntil(requireNotNull(effectiveUntil))

            SanctionAction.BAN -> target.ban()

            SanctionAction.FORCE_WITHDRAW -> target.forceWithdraw()

            // sanction()이 진입 시점에 걸러내므로 여기 도달하지 않는다. when 완전성을 위한 분기다
            SanctionAction.RELEASE -> throw BusinessException(CommonErrorCode.INVALID_REQUEST)
        }
    }

    /**
     * 제재·해제 공통 선행 검증 — 자기 자신과 관리자 계정은 이 경로의 대상이 아니다.
     * 감사 기록에 쓸 대상 이름도 여기서 집는다(조치 적용 전의 값이어야 한다).
     */
    private fun loadSanctionTarget(
        adminId: Long,
        targetUserId: Long,
    ): User {
        if (adminId == targetUserId) {
            throw BusinessException(UserErrorCode.SELF_SANCTION_NOT_ALLOWED)
        }
        // 행을 잠그고 읽는다 — 두 운영자가 동시에 제재·해제하면 뒤 커밋이 앞 상태를 덮는다
        val target =
            userRepository.findByIdForUpdate(targetUserId)
                ?: throw BusinessException(UserErrorCode.USER_NOT_FOUND)
        // 감사 행에 남길 그 시점의 대상 이름. 조치를 적용하기 전에 집는다 — 탈퇴 처리는 프로필을
        // 지워 닉네임을 없애므로, 뒤에서 읽으면 가장 무거운 제재의 행에 대상 이름이 비게 된다.
        AdminAuditContextHolder.label(target.nickname)
        // 관리자를 제재하면 운영 권한을 가진 계정이 차단 상태로 남는다. 강등을 먼저 하게 끊는다
        if (target.role == UserRole.ADMIN) {
            throw BusinessException(UserErrorCode.ADMIN_CANNOT_BE_SANCTIONED)
        }
        return target
    }

    private fun toCondition(query: MemberSearchQuery): MemberPageCondition {
        val decoded = query.cursor?.let(MemberCursorCodec::decode)
        // 상태·검색어·가입일 중 하나라도 바꾼 채 옛 커서를 보내면 두 집합이 섞이거나 사이 구간이 빠진다
        if (decoded != null && decoded.filterDigest != MemberCursorCodec.digestOf(query)) {
            throw BusinessException(CommonErrorCode.INVALID_CURSOR)
        }
        return MemberPageCondition(
            statuses = query.statusFilter.storedStatuses(),
            nicknameKeyword = query.keyword,
            userIdKeyword = query.keyword?.toLongOrNull(),
            joinedFrom = query.joinedFrom?.atStartOfDay(),
            // 반열림 구간 — 입력한 날짜의 하루치가 빠지지 않게 다음 날 0시를 상한으로 쓴다.
            // LocalDate.MAX는 다음 날이 없고 상한으로서 의미도 없어(모든 행이 그 이하) 조건에서 뺀다
            joinedToExclusive =
                query.joinedTo
                    ?.takeIf { it < LocalDate.MAX }
                    ?.plusDays(1)
                    ?.atStartOfDay(),
            lastUserId = decoded?.lastUserId,
            limit = CursorPageSize.fetchLimit(query.size),
        )
    }
}
