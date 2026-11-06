package com.aechak.application.sns.report.usecase

import com.aechak.application.sns.report.usecase.command.SubmitPostReportCommand

interface SnsReportUseCase {
    /** SNS 게시물 신고 접수. 신고자가 상세로 볼 수 있는 게시물만 받는다 */
    fun submitPostReport(command: SubmitPostReportCommand)
}
