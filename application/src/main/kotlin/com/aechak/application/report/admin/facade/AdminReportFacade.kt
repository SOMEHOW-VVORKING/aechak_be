package com.aechak.application.report.admin.facade

import com.aechak.application.inspection.port.ProductInspectionGate
import com.aechak.application.report.admin.port.view.ReportRowView
import com.aechak.application.report.admin.service.AdminReportService
import com.aechak.application.report.admin.support.ReportCursorCodec
import com.aechak.application.report.admin.usecase.AdminReportUseCase
import com.aechak.application.report.admin.usecase.command.DecideReportCommand
import com.aechak.application.report.admin.usecase.query.ReportSearchQuery
import com.aechak.application.report.admin.usecase.result.DecisionRecordResult
import com.aechak.application.report.admin.usecase.result.ReportDetailResult
import com.aechak.application.report.admin.usecase.result.ReportHistoryResult
import com.aechak.application.report.admin.usecase.result.ReportSummaryResult
import com.aechak.application.review.admin.usecase.AdminReviewUseCase
import com.aechak.application.review.admin.usecase.command.BlindReviewCommand
import com.aechak.application.support.CursorPageResult
import com.aechak.application.user.sanction.usecase.AdminMemberUseCase
import com.aechak.application.user.sanction.usecase.command.SanctionMemberCommand
import com.aechak.application.user.user.usecase.UserUseCase
import com.aechak.common.error.BusinessException
import com.aechak.domain.report.decision.ReportDecision
import com.aechak.domain.report.decision.enums.ReportDecisionReason
import com.aechak.domain.report.decision.enums.ReportDecisionType
import com.aechak.domain.report.decision.enums.ReportTargetType
import com.aechak.domain.report.decision.repository.DuplicateReportDecisionException
import com.aechak.domain.report.decision.repository.ReportDecisionRepository
import com.aechak.domain.report.error.ReportErrorCode
import com.aechak.domain.review.review.enums.ReviewBlindReason
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * AdminReportUseCase 의 유일한 구현체. 트랜잭션 경계는 여기 고정.
 *
 * 비노출·제재는 기존 경로에 위임한다 — 상품은 ProductInspectionGate.rejectByAdmin,
 * 리뷰는 AdminReviewUseCase.blind, 계정은 AdminMemberUseCase.sanction.
 * 결정 기록을 먼저 저장(flush)한 뒤 위임한다.
 * 안내 문구는 보관만 한다 — 어드민 보트에 발송 어댑터가 조립되어 있지 않다.
 */
@Service
class AdminReportFacade(
    private val adminReportService: AdminReportService,
    private val reportDecisionRepository: ReportDecisionRepository,
    private val productInspectionGate: ProductInspectionGate,
    private val adminReviewUseCase: AdminReviewUseCase,
    private val adminMemberUseCase: AdminMemberUseCase,
    private val userUseCase: UserUseCase,
) : AdminReportUseCase {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional(readOnly = true)
    override fun list(query: ReportSearchQuery): CursorPageResult<ReportSummaryResult> {
        val fetched = adminReportService.findPage(query)
        val nicknames = nicknamesOf(fetched)
        val counts = adminReportService.countByTargets(fetched)
        // 첫 페이지에만 전체 건수를 센다 — 세 표에 COUNT 를 매번 다시 돌리지 않는다
        val totalCount = if (query.cursor == null) adminReportService.countAll(query) else null

        return CursorPageResult.of(
            fetched =
                fetched.map {
                    ReportSummaryResult.from(
                        view = it,
                        ownerNickname = nicknames[it.targetOwnerUserId],
                        reportCount = counts[AdminReportService.TargetRef(it.targetType, it.targetId)] ?: 0L,
                    )
                },
            size = query.size,
            totalCount = totalCount,
            encodeCursor = { ReportCursorCodec.encode(query, it.reportedAt, it.targetType, it.reportId) },
        )
    }

    @Transactional(readOnly = true)
    override fun detail(
        targetType: ReportTargetType,
        reportId: Long,
    ): ReportDetailResult {
        val view = adminReportService.findOne(targetType, reportId)
        val nicknames = nicknamesOf(listOf(view))
        val counts = adminReportService.countByTargets(listOf(view))
        val decision = reportDecisionRepository.findByTarget(targetType, reportId)

        return ReportDetailResult(
            report =
                ReportSummaryResult.from(
                    view = view,
                    ownerNickname = nicknames[view.targetOwnerUserId],
                    reportCount = counts[AdminReportService.TargetRef(targetType, view.targetId)] ?: 0L,
                ),
            reporterId = view.reporterId,
            reasonText = view.reasonText,
            // 지금 보고 있는 건은 이력에서 뺀다
            history =
                adminReportService
                    .findHistory(targetType, view.targetId)
                    .filterNot { it.reportId == reportId }
                    .map(ReportHistoryResult::from),
            decision = decision?.let(DecisionRecordResult::from),
        )
    }

    @Transactional
    override fun decide(command: DecideReportCommand) {
        val view = adminReportService.findOne(command.targetType, command.reportId)
        validateSanctionDetail(command)

        // 적용할 수 없는 조합은 애그리거트가 막는다. 위임보다 먼저 통과해야 한다
        val decision =
            ReportDecision.decide(
                targetType = command.targetType,
                reportId = command.reportId,
                decision = command.decision,
                reasonCode = command.reasonCode,
                reasonDetail = command.reasonDetail,
                noticeToOwner = command.noticeToOwner,
                internalMemo = command.internalMemo,
                targetOwnerUserId = view.targetOwnerUserId,
                actedByAdminId = command.adminId,
            )
        // 중복은 DB 의 UNIQUE 가 판정한다 — 조회로 먼저 거르면 동시 요청 둘이 모두 통과한다
        val saved =
            try {
                reportDecisionRepository.save(decision)
            } catch (e: DuplicateReportDecisionException) {
                throw BusinessException(ReportErrorCode.REPORT_ALREADY_DECIDED, e)
            }

        // 사용자 신고는 숨길 콘텐츠가 없어 비노출 단계를 건너뛰고 제재로 바로 간다
        if (command.decision.hidesTarget() && command.targetType.hasHidableTarget()) {
            hideTarget(command, view)
        }
        command.sanction?.let { sanction(command, view, it) }

        log.info(
            "신고 처리 adminId={} targetType={} reportId={} decision={} reasonCode={} decisionId={}",
            command.adminId,
            command.targetType,
            command.reportId,
            command.decision,
            command.reasonCode,
            saved.id,
        )
    }

    /** 대상 비노출 위임. 이미 가려진 대상은 건너뛴다 — 다시 걸면 409 로 거절돼 처리가 막힌다. */
    private fun hideTarget(
        command: DecideReportCommand,
        view: ReportRowView,
    ) {
        if (view.targetAlreadyHidden) {
            log.info(
                "대상이 이미 비노출이라 비노출 위임을 건너뛴다 targetType={} reportId={}",
                command.targetType,
                command.reportId,
            )
            return
        }
        when (command.targetType) {
            // 판매 상태(셀러의 의사)가 아니라 검수 상태(플랫폼의 노출 판단)를 내린다.
            // [한계] 검수 게이트(inspection.gate-enabled)를 켜면 이후 PASS 판정이 이 반려를 되돌린다 —
            // 반려 유지 판정이 검토 큐의 관리자 결정만 보고 신고 결정은 보지 않는다
            ReportTargetType.PRODUCT -> {
                productInspectionGate.rejectByAdmin(view.targetId)
            }

            ReportTargetType.REVIEW -> {
                adminReviewUseCase.blind(
                    BlindReviewCommand(
                        command.adminId,
                        view.targetId,
                        toBlindReason(command.reasonCode),
                        command.reasonDetail,
                    ),
                )
            }

            // ReportDecision.decide 가 이미 막아 도달하지 않는다
            ReportTargetType.USER -> {
                throw BusinessException(ReportErrorCode.REPORT_DECISION_NOT_APPLICABLE)
            }
        }
    }

    /** 계정 제재 위임. 운영자가 고른 제재 어휘를 변환하지 않고 그대로 넘긴다. */
    private fun sanction(
        command: DecideReportCommand,
        view: ReportRowView,
        detail: DecideReportCommand.SanctionDetail,
    ) {
        adminMemberUseCase.sanction(
            SanctionMemberCommand(
                adminId = command.adminId,
                targetUserId = view.targetOwnerUserId,
                action = detail.action,
                reasonCode = detail.reasonCode,
                reasonDetail = detail.reasonDetail,
                durationDays = detail.durationDays,
                // 회원 상세의 제재 이력에서도 사유가 보이게 같은 메모를 남긴다
                memo = command.internalMemo,
            ),
        )
    }

    /** 계정 제재는 제재 내용이 함께 와야 하고, 다른 처리에는 올 수 없다. */
    private fun validateSanctionDetail(command: DecideReportCommand) {
        val required = command.decision == ReportDecisionType.SANCTION_ACCOUNT
        if (required && command.sanction == null) {
            throw BusinessException(ReportErrorCode.REPORT_SANCTION_DETAIL_REQUIRED)
        }
        if (!required && command.sanction != null) {
            throw BusinessException(ReportErrorCode.REPORT_SANCTION_DETAIL_NOT_ALLOWED)
        }
    }

    private fun nicknamesOf(rows: List<ReportRowView>): Map<Long, String> =
        userUseCase
            .getAuthors(rows.map { it.targetOwnerUserId }.distinct())
            .mapValues { it.value.nickname }

    /** 신고 처리 사유를 리뷰 블라인드 사유 어휘로 옮긴다. */
    private fun toBlindReason(reason: ReportDecisionReason): ReviewBlindReason =
        when (reason) {
            ReportDecisionReason.FALSE_AD -> ReviewBlindReason.AD_SPAM

            ReportDecisionReason.ABUSE -> ReviewBlindReason.ABUSE

            ReportDecisionReason.EXTERNAL_TRADE -> ReviewBlindReason.EXTERNAL_TRADE

            ReportDecisionReason.ILLEGAL -> ReviewBlindReason.OTHER

            // 제재로 가지 않는 두 사유는 ReportDecision.decide 가 막아 도달하지 않는다
            ReportDecisionReason.FALSE_REPORT, ReportDecisionReason.NOT_CONFIRMED -> ReviewBlindReason.OTHER
        }
}
