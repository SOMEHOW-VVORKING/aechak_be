package com.aechak.application.review.report.usecase

import com.aechak.application.review.report.usecase.command.SubmitReviewReportCommand

interface ReviewReportUseCase {
    /** 리뷰 신고 접수 */
    fun submitReviewReport(command: SubmitReviewReportCommand)

    /** 신고할 수 있는(공개 중인) 리뷰의 작성자 id. 없거나 공개 중이 아니면 null */
    fun getReportableAuthorId(reviewId: Long): Long?
}
