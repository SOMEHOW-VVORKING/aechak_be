package com.aechak.domain.sns.report

import com.aechak.common.error.BusinessException
import com.aechak.domain.sns.error.SnsErrorCode
import com.aechak.domain.sns.post.Post
import com.aechak.domain.sns.report.enums.PostReportReason
import com.aechak.domain.sns.report.event.PostReportReceivedEvent
import com.aechak.domain.support.AggregateRoot
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

/** SNS 게시물 신고. 게시물이 나중에 지워져도 행은 남는다 */
@Entity
@Table(
    name = "sns_post_reports",
    uniqueConstraints = [UniqueConstraint(name = PostReport.UK_POST_ID_REPORTER_ID, columnNames = ["post_id", "reporter_id"])],
)
class PostReport protected constructor(
    @Column(name = "post_id", nullable = false)
    val postId: Long,
    @Column(name = "reporter_id", nullable = false)
    val reporterId: Long,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    val reasonCode: PostReportReason,
    @Column(length = REASON_TEXT_MAX)
    val reasonText: String?,
) : AggregateRoot() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L

    /** 접수 사실 이벤트 등록. 저장해 id가 생긴 뒤 호출한다 */
    fun registerReceived(postPublicId: String) =
        registerEvent(
            PostReportReceivedEvent(
                reportId = id,
                postPublicId = postPublicId,
                reasonCode = reasonCode,
                reasonText = reasonText,
                createdAt = createdAt,
            ),
        )

    companion object {
        const val UK_POST_ID_REPORTER_ID = "uk_sns_post_reports_post_reporter"
        const val REASON_TEXT_MAX = 500

        fun report(
            post: Post,
            reporterId: Long,
            reasonCode: PostReportReason,
            reasonText: String?,
        ): PostReport {
            if (post.authorId == reporterId) throw BusinessException(SnsErrorCode.SNS_POST_REPORT_SELF)
            val text = reasonText?.trim()?.takeIf { it.isNotEmpty() }
            if (reasonCode == PostReportReason.OTHER && text == null) {
                throw BusinessException(SnsErrorCode.SNS_POST_REPORT_REASON_TEXT_REQUIRED)
            }
            return PostReport(post.id, reporterId, reasonCode, text)
        }
    }
}
