package com.aechak.application.user.report.facade

import com.aechak.application.review.report.usecase.ReviewReportUseCase
import com.aechak.application.user.report.service.UserReportService
import com.aechak.application.user.report.usecase.UserReportUseCase
import com.aechak.application.user.report.usecase.command.SubmitUserReportCommand
import com.aechak.common.error.BusinessException
import com.aechak.domain.user.error.UserErrorCode
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UserReportFacade(
    private val userReportService: UserReportService,
    private val reviewReportUseCase: ReviewReportUseCase,
) : UserReportUseCase {
    @Transactional
    override fun submitUserReport(command: SubmitUserReportCommand) {
        // 신고 대상은 공개 중인 리뷰의 작성자다
        val targetUserId =
            reviewReportUseCase.getReportableAuthorId(command.reviewId)
                ?: throw BusinessException(UserErrorCode.USER_NOT_FOUND)
        userReportService.report(command, targetUserId)
    }
}
