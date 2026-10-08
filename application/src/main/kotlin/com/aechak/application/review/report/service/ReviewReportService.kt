package com.aechak.application.review.report.service

import com.aechak.application.review.report.usecase.command.SubmitReviewReportCommand
import com.aechak.common.error.BusinessException
import com.aechak.domain.review.error.ReviewErrorCode
import com.aechak.domain.review.report.repository.DuplicateReviewReportException
import com.aechak.domain.review.report.repository.ReviewReportRepository
import com.aechak.domain.review.review.Review
import com.aechak.domain.review.review.repository.ReviewRepository
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service

@Service
class ReviewReportService(
    private val reviewRepository: ReviewRepository,
    private val reviewReportRepository: ReviewReportRepository,
    private val eventPublisher: ApplicationEventPublisher,
) {
    fun report(command: SubmitReviewReportCommand) {
        val review = findReportable(command.reviewId) ?: throw BusinessException(ReviewErrorCode.REVIEW_NOT_FOUND)
        val report =
            try {
                reviewReportRepository.save(command.toEntity(review))
            } catch (e: DuplicateReviewReportException) {
                throw BusinessException(ReviewErrorCode.REVIEW_ALREADY_REPORTED, e)
            }
        report.registerReceived()
        report.events.forEach { eventPublisher.publishEvent(it) }
        report.clearEvents()
    }

    fun findReportableAuthorId(reviewId: Long): Long? = findReportable(reviewId)?.authorUserId

    /** 공개 중인(PUBLIC, MASKED) 리뷰만 신고할 수 있다 */
    private fun findReportable(reviewId: Long): Review? = reviewRepository.findById(reviewId)?.takeIf { it.reviewStatus.isVisible() }
}
