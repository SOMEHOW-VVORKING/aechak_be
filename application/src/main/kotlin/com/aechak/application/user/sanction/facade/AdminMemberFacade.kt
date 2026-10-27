package com.aechak.application.user.sanction.facade

import com.aechak.application.support.CursorPageResult
import com.aechak.application.user.sanction.service.AdminMemberService
import com.aechak.application.user.sanction.support.MemberCursorCodec
import com.aechak.application.user.sanction.usecase.AdminMemberUseCase
import com.aechak.application.user.sanction.usecase.command.ReleaseSanctionCommand
import com.aechak.application.user.sanction.usecase.command.SanctionMemberCommand
import com.aechak.application.user.sanction.usecase.query.MemberSearchQuery
import com.aechak.application.user.sanction.usecase.result.MemberDetailResult
import com.aechak.application.user.sanction.usecase.result.MemberSummaryResult
import com.aechak.application.user.sanction.usecase.result.SanctionHistoryResult
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/** AdminMemberUseCase의 유일한 구현체. 트랜잭션 경계는 여기 고정. */
@Service
class AdminMemberFacade(
    private val adminMemberService: AdminMemberService,
) : AdminMemberUseCase {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional(readOnly = true)
    override fun listMembers(query: MemberSearchQuery): CursorPageResult<MemberSummaryResult> {
        // 만료 판정 기준 시각을 한 번만 집어 한 페이지 안에서 행마다 다른 "지금"이 쓰이지 않게 한다
        val at = LocalDateTime.now()
        return CursorPageResult.of(
            fetched = adminMemberService.findMemberPage(query).map { MemberSummaryResult.from(it, at) },
            size = query.size,
            totalCount = adminMemberService.countMembers(query),
            encodeCursor = { MemberCursorCodec.encode(query.statusFilter, it.userId) },
        )
    }

    @Transactional(readOnly = true)
    override fun getMember(userId: Long): MemberDetailResult =
        MemberDetailResult(
            member = MemberSummaryResult.from(adminMemberService.findMember(userId), LocalDateTime.now()),
            sanctions =
                adminMemberService
                    .findSanctionHistory(userId, SANCTION_HISTORY_LIMIT)
                    .map(SanctionHistoryResult::from),
        )

    @Transactional
    override fun sanction(command: SanctionMemberCommand) {
        adminMemberService.sanction(command)
        log.info(
            "회원 제재 adminId={} targetUserId={} action={} reasonCode={}",
            command.adminId,
            command.targetUserId,
            command.action,
            command.reasonCode,
        )
    }

    @Transactional
    override fun release(command: ReleaseSanctionCommand) {
        adminMemberService.release(command)
        log.info("회원 제재 해제 adminId={} targetUserId={}", command.adminId, command.targetUserId)
    }

    private companion object {
        /** 상세 화면이 보여 주는 이력 상한 — 운영 판단에 필요한 최근 이력만 싣는다. */
        const val SANCTION_HISTORY_LIMIT = 50
    }
}
