package com.aechak.application.user.sanction.service

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

    /** 제재·해제 공통 선행 검증 — 자기 자신과 관리자 계정은 이 경로의 대상이 아니다. */
    private fun loadSanctionTarget(
        adminId: Long,
        targetUserId: Long,
    ): User {
        if (adminId == targetUserId) {
            throw BusinessException(UserErrorCode.SELF_SANCTION_NOT_ALLOWED)
        }
        val target =
            userRepository.findById(targetUserId)
                ?: throw BusinessException(UserErrorCode.USER_NOT_FOUND)
        // 관리자를 제재하면 운영 권한을 가진 계정이 차단 상태로 남는다. 강등을 먼저 하게 끊는다
        if (target.role == UserRole.ADMIN) {
            throw BusinessException(UserErrorCode.ADMIN_CANNOT_BE_SANCTIONED)
        }
        return target
    }

    private fun toCondition(query: MemberSearchQuery): MemberPageCondition {
        val decoded = query.cursor?.let(MemberCursorCodec::decode)
        // 필터를 바꾼 채 옛 커서를 보내면 두 집합이 한 목록에 섞인다 — 처음부터 다시 받는다
        if (decoded != null && decoded.filter != query.statusFilter) {
            throw BusinessException(CommonErrorCode.INVALID_CURSOR)
        }
        return MemberPageCondition(
            statuses = query.statusFilter.storedStatuses(),
            nicknameKeyword = query.keyword,
            userIdKeyword = query.keyword?.toLongOrNull(),
            joinedFrom = query.joinedFrom?.atStartOfDay(),
            // 반열림 구간 — 입력한 날짜의 하루치가 빠지지 않게 다음 날 0시를 상한으로 쓴다
            joinedToExclusive = query.joinedTo?.plusDays(1)?.atStartOfDay(),
            lastUserId = decoded?.lastUserId,
            limit = CursorPageSize.fetchLimit(query.size),
        )
    }
}
