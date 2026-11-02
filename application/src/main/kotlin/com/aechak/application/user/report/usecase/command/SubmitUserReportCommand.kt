package com.aechak.application.user.report.usecase.command

import com.aechak.domain.user.report.UserReport
import com.aechak.domain.user.report.enums.ReportReasonCode
import com.aechak.domain.user.user.User

/** reviewId는 신고할 사용자가 쓴 리뷰의 id */
data class SubmitUserReportCommand(
    val reporterId: Long,
    val reviewId: Long,
    val reasonCode: ReportReasonCode,
    val reasonText: String?,
) {
    fun toEntity(
        reporter: User,
        targetUser: User,
    ): UserReport = UserReport.report(reporter, targetUser, reasonCode, reasonText)
}
