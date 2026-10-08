package com.aechak.domain.review.report

import com.aechak.common.error.BusinessException
import com.aechak.domain.review.error.ReviewErrorCode
import com.aechak.domain.review.report.enums.ReviewReportReason
import com.aechak.domain.review.report.enums.ReviewReportStatus
import com.aechak.domain.review.report.event.ReviewReportReceivedEvent
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
import jakarta.persistence.UniqueConstraint

@Entity
@Table(
    name = "review_reports",
    uniqueConstraints = [
        UniqueConstraint(name = ReviewReport.UK_REVIEW_ID_REPORTER_ID, columnNames = ["review_id", "reporter_id"]),
    ],
)
class ReviewReport protected constructor(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "review_id", nullable = false)
    val review: Review,
    val reporterId: Long,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    val reasonCode: ReviewReportReason,
    @Column(length = REASON_TEXT_MAX)
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

    /** 접수 사실 이벤트 등록. 저장해 id가 생긴 뒤 호출한다 */
    fun registerReceived() =
        registerEvent(
            ReviewReportReceivedEvent(
                reportId = id,
                reviewId = review.id,
                reasonCode = reasonCode,
                reasonText = reasonText,
                createdAt = createdAt,
            ),
        )

    companion object {
        const val UK_REVIEW_ID_REPORTER_ID = "uk_review_reports_review_id_reporter_id"
        const val REASON_TEXT_MAX = 500

        fun report(
            review: Review,
            reporterId: Long,
            reasonCode: ReviewReportReason,
            reasonText: String?,
        ): ReviewReport {
            if (review.authorUserId == reporterId) {
                throw BusinessException(ReviewErrorCode.REVIEW_SELF_REPORT_NOT_ALLOWED)
            }
            val text = reasonText?.trim()?.takeIf { it.isNotEmpty() }
            if (reasonCode == ReviewReportReason.OTHER && text == null) {
                throw BusinessException(ReviewErrorCode.REVIEW_REPORT_REASON_TEXT_REQUIRED)
            }
            return ReviewReport(review, reporterId, reasonCode, text)
        }
    }
}
