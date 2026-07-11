package com.aechak.domain.user.report

import com.aechak.common.error.BusinessException
import com.aechak.domain.support.BaseEntity
import com.aechak.domain.user.error.UserErrorCode
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
import com.aechak.domain.user.report.enums.ReportStatus
import com.aechak.domain.user.report.enums.ReportReasonCode
import com.aechak.domain.user.user.User

@Entity
@Table(name = "user_reports")
class UserReport protected constructor(
    reporter: User,
    targetUser: User,
    reasonCode: ReportReasonCode,
    reasonText: String?,
) : BaseEntity() {

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

    @Column(length = 500)
    val reasonText: String? = reasonText

    @Enumerated(EnumType.STRING)
    @Column(length = 30, nullable = false)
    var status: ReportStatus = ReportStatus.RECEIVED
        protected set


    companion object {
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
            if (reasonCode == ReportReasonCode.OTHER && reasonText.isNullOrBlank()) {
                throw BusinessException(UserErrorCode.REPORT_REASON_REQUIRED)
            }
            return UserReport(reporter, targetUser, reasonCode, reasonText?.trim())
        }
    }
}
