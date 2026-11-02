package com.aechak.domain.user.report

import com.aechak.common.error.BusinessException
import com.aechak.domain.support.AggregateRoot
import com.aechak.domain.user.error.UserErrorCode
import com.aechak.domain.user.report.enums.ReportReasonCode
import com.aechak.domain.user.report.enums.ReportStatus
import com.aechak.domain.user.report.event.UserReportReceivedEvent
import com.aechak.domain.user.user.User
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
    name = "user_reports",
    uniqueConstraints = [
        UniqueConstraint(name = UserReport.UK_TARGET_USER_ID_REPORTER_ID, columnNames = ["target_user_id", "reporter_id"]),
    ],
)
class UserReport protected constructor(
    reporter: User,
    targetUser: User,
    reasonCode: ReportReasonCode,
    reasonText: String?,
) : AggregateRoot() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reporter_id", nullable = false)
    val reporter: User = reporter

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_user_id", nullable = false)
    val targetUser: User = targetUser

    @Enumerated(EnumType.STRING)
    @Column(length = 30, nullable = false)
    val reasonCode: ReportReasonCode = reasonCode

    @Column(length = REASON_TEXT_MAX)
    val reasonText: String? = reasonText

    @Enumerated(EnumType.STRING)
    @Column(length = 30, nullable = false)
    var status: ReportStatus = ReportStatus.RECEIVED
        protected set

    /** 접수 사실 이벤트 등록. 저장해 id가 생긴 뒤 호출한다. reviewId는 신고 대상을 고른 리뷰의 id */
    fun registerReceived(reviewId: Long) =
        registerEvent(
            UserReportReceivedEvent(
                reportId = id,
                targetUserId = targetUser.id,
                reviewId = reviewId,
                reasonCode = reasonCode,
                reasonText = reasonText,
                createdAt = createdAt,
            ),
        )

    companion object {
        const val UK_TARGET_USER_ID_REPORTER_ID = "uk_user_reports_target_user_id_reporter_id"
        const val REASON_TEXT_MAX = 500

        /** 미영속(id=0) 유저 비교 함정 — 영속 유저 전제. */
        fun report(
            reporter: User,
            targetUser: User,
            reasonCode: ReportReasonCode,
            reasonText: String? = null,
        ): UserReport {
            if (reporter.id == targetUser.id) {
                throw BusinessException(UserErrorCode.SELF_REPORT_NOT_ALLOWED)
            }
            val text = reasonText?.trim()?.takeIf { it.isNotEmpty() }
            if (reasonCode == ReportReasonCode.OTHER && text == null) {
                throw BusinessException(UserErrorCode.REPORT_REASON_REQUIRED)
            }
            return UserReport(reporter, targetUser, reasonCode, text)
        }
    }
}
