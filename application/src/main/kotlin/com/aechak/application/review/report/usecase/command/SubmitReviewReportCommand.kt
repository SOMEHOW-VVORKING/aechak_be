package com.aechak.application.review.report.usecase.command

import com.aechak.domain.review.report.ReviewReport
import com.aechak.domain.review.report.enums.ReviewReportReason
import com.aechak.domain.review.review.Review

data class SubmitReviewReportCommand(
    val reporterId: Long,
    val reviewId: Long,
    val reasonCode: ReviewReportReason,
    val reasonText: String?,
) {
    fun toEntity(review: Review): ReviewReport = ReviewReport.report(review, reporterId, reasonCode, reasonText)
}
