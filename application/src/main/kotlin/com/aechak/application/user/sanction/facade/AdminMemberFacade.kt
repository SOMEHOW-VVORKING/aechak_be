package com.aechak.application.user.sanction.facade

import com.aechak.application.file.port.enums.UploadPurpose
import com.aechak.application.file.usecase.FileUseCase
import com.aechak.application.file.usecase.command.DeleteFileCommand
import com.aechak.application.sns.withdrawal.usecase.SnsWithdrawalUseCase
import com.aechak.application.support.CursorPageResult
import com.aechak.application.user.sanction.service.AdminMemberService
import com.aechak.application.user.sanction.support.MemberCursorCodec
import com.aechak.application.user.sanction.usecase.AdminMemberUseCase
import com.aechak.application.user.sanction.usecase.command.ReleaseSanctionCommand
import com.aechak.application.user.sanction.usecase.command.ResetMemberProfileCommand
import com.aechak.application.user.sanction.usecase.command.SanctionMemberCommand
import com.aechak.application.user.sanction.usecase.query.MemberSearchQuery
import com.aechak.application.user.sanction.usecase.result.MemberDetailResult
import com.aechak.application.user.sanction.usecase.result.MemberSummaryResult
import com.aechak.application.user.sanction.usecase.result.SanctionHistoryResult
import com.aechak.domain.user.sanction.enums.SanctionAction
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.time.LocalDateTime

/** AdminMemberUseCase의 유일한 구현체. 트랜잭션 경계는 여기 고정. */
@Service
class AdminMemberFacade(
    private val adminMemberService: AdminMemberService,
    private val fileUseCase: FileUseCase,
    private val snsWithdrawalUseCase: SnsWithdrawalUseCase,
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
            encodeCursor = { MemberCursorCodec.encode(query, it.userId) },
        )
    }

    @Transactional(readOnly = true)
    override fun getMember(userId: Long): MemberDetailResult {
        val member = MemberSummaryResult.from(adminMemberService.findMember(userId), LocalDateTime.now())
        val user = adminMemberService.findProfileUser(userId)
        return MemberDetailResult(
            member = member,
            profileImageUrl = fileUseCase.resolveMediaUrl(user.profileImageKey),
            bio = user.bio,
            sanctions =
                adminMemberService
                    .findSanctionHistory(userId, SANCTION_HISTORY_LIMIT)
                    .map(SanctionHistoryResult::from),
        )
    }

    /** 탈퇴 처리는 자기 탈퇴처럼 SNS 데이터를 정리하고 커밋 뒤에 프로필 이미지를 지운다. 신고 처리 트랜잭션 안에서도 불린다 */
    @Transactional
    override fun sanction(command: SanctionMemberCommand) {
        val profileImageKey = adminMemberService.sanction(command)
        if (command.action == SanctionAction.FORCE_WITHDRAW) {
            snsWithdrawalUseCase.cleanUp(command.targetUserId)
            profileImageKey?.let { deleteProfileImageAfterCommit(command.targetUserId, it) }
        }
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

    @Transactional
    override fun resetProfile(command: ResetMemberProfileCommand) {
        adminMemberService.resetProfile(command)
        log.info("회원 프로필 초기화 adminId={} targetUserId={}", command.adminId, command.targetUserId)
    }

    private fun deleteProfileImageAfterCommit(
        userId: Long,
        key: String,
    ) {
        TransactionSynchronizationManager.registerSynchronization(
            object : TransactionSynchronization {
                override fun afterCommit() {
                    runCatching { fileUseCase.delete(DeleteFileCommand(key, UploadPurpose.USER_PROFILE)) }
                        .onFailure { log.error("강제 탈퇴 후 프로필 이미지 삭제 실패, 수동 확인 필요 (userId={}, key={})", userId, key, it) }
                }
            },
        )
    }

    private companion object {
        /** 상세 화면이 보여 주는 이력 상한 — 운영 판단에 필요한 최근 이력만 싣는다. */
        const val SANCTION_HISTORY_LIMIT = 50
    }
}
