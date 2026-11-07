package com.aechak.application.sns.report.usecase

import com.aechak.application.sns.report.usecase.command.SubmitCommentReportCommand
import com.aechak.application.sns.report.usecase.command.SubmitPostReportCommand
import com.aechak.application.user.report.usecase.command.SubmitUserReportCommand

interface SnsReportUseCase {
    /** SNS 게시물 신고 접수. 신고자가 상세로 볼 수 있는 게시물만 받는다 */
    fun submitPostReport(command: SubmitPostReportCommand)

    /** SNS 댓글과 대댓글 신고 접수. 신고자에게 댓글 목록으로 보이는 댓글만 받는다 */
    fun submitCommentReport(command: SubmitCommentReportCommand)

    /** SNS 프로필 사용자 신고 접수. 두 사람 사이의 차단과 관계없이 받는다 */
    fun submitUserReport(
        targetPublicId: String,
        command: SubmitUserReportCommand,
    )
}
