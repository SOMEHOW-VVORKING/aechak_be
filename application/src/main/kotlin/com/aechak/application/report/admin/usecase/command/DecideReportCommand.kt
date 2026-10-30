package com.aechak.application.report.admin.usecase.command

import com.aechak.domain.report.decision.enums.ReportDecisionReason
import com.aechak.domain.report.decision.enums.ReportDecisionType
import com.aechak.domain.report.decision.enums.ReportTargetType
import com.aechak.domain.user.sanction.enums.SanctionAction
import com.aechak.domain.user.sanction.enums.SanctionReasonCode

/** 신고 처리 입력. `sanction` 은 `decision == SANCTION_ACCOUNT` 일 때만 온다. */
data class DecideReportCommand(
    val adminId: Long,
    val targetType: ReportTargetType,
    val reportId: Long,
    val decision: ReportDecisionType,
    val reasonCode: ReportDecisionReason,
    val reasonDetail: String?,
    val noticeToOwner: String?,
    val internalMemo: String?,
    val sanction: SanctionDetail?,
) {
    /** 계정 제재의 내용. 기존 회원 제재 경로에 그대로 넘어간다. */
    data class SanctionDetail(
        val action: SanctionAction,
        val reasonCode: SanctionReasonCode,
        val reasonDetail: String?,
        val durationDays: Int?,
    )
}
