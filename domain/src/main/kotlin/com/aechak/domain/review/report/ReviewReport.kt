package com.aechak.domain.review.report

import com.aechak.common.error.BusinessException
import com.aechak.domain.review.error.ReviewErrorCode
import com.aechak.domain.review.report.enums.ReviewReportReason
import com.aechak.domain.review.report.enums.ReviewReportStatus
import com.aechak.domain.review.review.Review
import com.aechak.domain.support.AggregateRoot
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

@Entity
@Table(name = "review_reports")
class ReviewReport protected constructor(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "review_id", nullable = false)
    val review: Review,
    val reporterId: Long,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    val reasonCode: ReviewReportReason,
    @Column(length = 500)
    val reasonText: String?,
) : AggregateRoot() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    var status: ReviewReportStatus = ReviewReportStatus.PENDING
        protected set

    fun resolve() {
        transitionFromPending(ReviewReportStatus.RESOLVED)
    }

    fun reject() {
        transitionFromPending(ReviewReportStatus.REJECTED)
    }

    private fun transitionFromPending(target: ReviewReportStatus) {
        if (status != ReviewReportStatus.PENDING) {
            throw BusinessException(ReviewErrorCode.INVALID_REVIEW_REPORT_STATUS_TRANSITION)
        }
        status = target
    }

    companion object {
        fun report(
            review: Review,
            reporterId: Long,
            reasonCode: ReviewReportReason,
            reasonText: String?,
        ): ReviewReport = ReviewReport(review, reporterId, reasonCode, reasonText)
    }
}
