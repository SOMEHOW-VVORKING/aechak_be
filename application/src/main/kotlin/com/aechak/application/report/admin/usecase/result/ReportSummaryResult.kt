package com.aechak.application.report.admin.usecase.result

import com.aechak.application.report.admin.port.view.ReportRowView
import com.aechak.domain.report.decision.enums.ReportDecisionReason
import com.aechak.domain.report.decision.enums.ReportDecisionType
import com.aechak.domain.report.decision.enums.ReportHandlingStatus
import com.aechak.domain.report.decision.enums.ReportTargetType
import java.time.LocalDateTime

/** 목록 한 행. 처리 상태의 파생 규칙(statusOf)을 여기 한 곳에만 둔다. */
data class ReportSummaryResult(
    val targetType: ReportTargetType,
    val reportId: Long,
    val targetKey: String,
    val targetName: String?,
    val targetOwnerUserId: Long,
    val targetOwnerNickname: String?,
    val reasonCode: String,
    val reportCount: Long,
    val reportedAt: LocalDateTime,
    val status: ReportHandlingStatus,
    val decision: ReportDecisionType?,
    val decisionReasonCode: ReportDecisionReason?,
    val decidedAt: LocalDateTime?,
) {
    companion object {
        fun from(
            view: ReportRowView,
            ownerNickname: String?,
            reportCount: Long,
        ): ReportSummaryResult =
            ReportSummaryResult(
                targetType = view.targetType,
                reportId = view.reportId,
                targetKey = view.targetKey,
                targetName = view.targetName,
                targetOwnerUserId = view.targetOwnerUserId,
                targetOwnerNickname = ownerNickname,
                reasonCode = view.reasonCode,
                reportCount = reportCount,
                reportedAt = view.reportedAt,
                status = statusOf(view.decision),
                decision = view.decision,
                decisionReasonCode = view.decisionReasonCode,
                decidedAt = view.decidedAt,
            )

        /** 결정 기록이 없으면 미처리, 있으면 제재였는지로 갈린다. */
        fun statusOf(decision: ReportDecisionType?): ReportHandlingStatus =
            when {
                decision == null -> ReportHandlingStatus.RECEIVED
                decision.sanctions() -> ReportHandlingStatus.SANCTIONED
                else -> ReportHandlingStatus.DISMISSED
            }
    }
}
