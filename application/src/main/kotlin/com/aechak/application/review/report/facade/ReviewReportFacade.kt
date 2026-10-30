package com.aechak.application.review.report.facade

import com.aechak.application.review.report.service.ReviewReportService
import com.aechak.application.review.report.usecase.ReviewReportUseCase
import com.aechak.application.review.report.usecase.command.SubmitReviewReportCommand
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ReviewReportFacade(
    private val reviewReportService: ReviewReportService,
) : ReviewReportUseCase {
    @Transactional
    override fun submitReviewReport(command: SubmitReviewReportCommand) = reviewReportService.report(command)

    @Transactional(readOnly = true)
    override fun getReportableAuthorId(reviewId: Long): Long? = reviewReportService.findReportableAuthorId(reviewId)
}
