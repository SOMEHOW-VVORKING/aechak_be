package com.aechak.infra.persistence.review

import com.aechak.domain.review.report.ReviewReport
import com.aechak.domain.review.report.repository.DuplicateReviewReportException
import com.aechak.domain.review.report.repository.ReviewReportRepository
import com.aechak.infra.persistence.support.isUniqueViolationOf
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

interface ReviewReportJpaRepository : JpaRepository<ReviewReport, Long>

@Repository
class ReviewReportRepositoryAdapter(
    private val jpaRepository: ReviewReportJpaRepository,
) : ReviewReportRepository {
    override fun save(report: ReviewReport): ReviewReport =
        try {
            jpaRepository.saveAndFlush(report)
        } catch (e: DataIntegrityViolationException) {
            if (e.isUniqueViolationOf(ReviewReport.UK_REVIEW_ID_REPORTER_ID)) {
                throw DuplicateReviewReportException(e)
            }
            throw e
        }
}
