package com.aechak.application.report.admin.usecase.result

import com.aechak.application.report.admin.port.view.ReportRowView
import com.aechak.domain.report.decision.ReportDecision
import com.aechak.domain.report.decision.enums.ReportDecisionReason
import com.aechak.domain.report.decision.enums.ReportDecisionType
import com.aechak.domain.report.decision.enums.ReportHandlingStatus
import com.aechak.domain.report.decision.enums.ReportTargetType
import java.time.LocalDateTime

/** 신고 상세. 같은 대상에 쌓인 신고 이력을 함께 싣는다. */
data class ReportDetailResult(
    val report: ReportSummaryResult,
    val reporterId: Long,
    val reasonText: String?,
    val history: List<ReportHistoryResult>,
    val decision: DecisionRecordResult?,
)

/** 같은 대상의 다른 신고 한 건. */
data class ReportHistoryResult(
    val reportId: Long,
    val reasonCode: String,
    val reasonText: String?,
    val reportedAt: LocalDateTime,
    val status: ReportHandlingStatus,
) {
    companion object {
        fun from(view: ReportRowView): ReportHistoryResult =
            ReportHistoryResult(
                reportId = view.reportId,
                reasonCode = view.reasonCode,
                reasonText = view.reasonText,
                reportedAt = view.reportedAt,
                status = ReportSummaryResult.statusOf(view.decision),
            )
    }
}

/** 이미 내려진 결정의 기록. 어드민 전용 경로라 내부 메모까지 싣는다. */
data class DecisionRecordResult(
    val targetType: ReportTargetType,
    val reportId: Long,
    val decision: ReportDecisionType,
    val reasonCode: ReportDecisionReason,
    val reasonDetail: String?,
    val noticeToOwner: String?,
    val internalMemo: String?,
    val targetOwnerUserId: Long,
    val actedByAdminId: Long,
    val decidedAt: LocalDateTime,
) {
    companion object {
        fun from(decision: ReportDecision): DecisionRecordResult =
            DecisionRecordResult(
                targetType = decision.targetType,
                reportId = decision.reportId,
                decision = decision.decision,
                reasonCode = decision.reasonCode,
                reasonDetail = decision.reasonDetail,
                noticeToOwner = decision.noticeToOwner,
                internalMemo = decision.internalMemo,
                targetOwnerUserId = decision.targetOwnerUserId,
                actedByAdminId = decision.actedByAdminId,
                decidedAt = decision.createdAt,
            )
    }
}
