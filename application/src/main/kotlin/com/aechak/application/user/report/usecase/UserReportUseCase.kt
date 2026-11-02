package com.aechak.application.user.report.usecase

import com.aechak.application.user.report.usecase.command.SubmitUserReportCommand

interface UserReportUseCase {
    /** 사용자 신고 접수. 신고 대상은 command.reviewId 리뷰의 작성자 */
    fun submitUserReport(command: SubmitUserReportCommand)
}
