package com.aechak.domain.sns.report

import com.aechak.common.error.BusinessException
import com.aechak.domain.sns.comment.Comment
import com.aechak.domain.sns.error.SnsErrorCode
import com.aechak.domain.sns.report.enums.PostReportReason
import com.aechak.domain.sns.report.event.CommentReportReceivedEvent
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

/** SNS 댓글 신고. 대댓글도 그 대댓글만 대상이다. 댓글이 나중에 지워져도 행은 남는다 */
@Entity
@Table(
    name = "sns_comment_reports",
    uniqueConstraints = [UniqueConstraint(name = CommentReport.UK_COMMENT_ID_REPORTER_ID, columnNames = ["comment_id", "reporter_id"])],
)
class CommentReport protected constructor(
    @Column(name = "comment_id", nullable = false)
    val commentId: Long,
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
    fun registerReceived(
        commentPublicId: String,
        postPublicId: String,
    ) = registerEvent(
        CommentReportReceivedEvent(
            reportId = id,
            commentPublicId = commentPublicId,
            postPublicId = postPublicId,
            reasonCode = reasonCode,
            reasonText = reasonText,
            createdAt = createdAt,
        ),
    )

    companion object {
        const val UK_COMMENT_ID_REPORTER_ID = "uk_sns_comment_reports_comment_reporter"
        const val REASON_TEXT_MAX = 500

        /** 사유 어휘와 상세 내용 규칙은 게시물 신고와 같다 */
        fun report(
            comment: Comment,
            reporterId: Long,
            reasonCode: PostReportReason,
            reasonText: String?,
        ): CommentReport {
            if (comment.authorId == reporterId) throw BusinessException(SnsErrorCode.SNS_COMMENT_REPORT_SELF)
            val text = reasonText?.trim()?.takeIf { it.isNotEmpty() }
            if (reasonCode == PostReportReason.OTHER && text == null) {
                throw BusinessException(SnsErrorCode.SNS_POST_REPORT_REASON_TEXT_REQUIRED)
            }
            return CommentReport(comment.id, reporterId, reasonCode, text)
        }
    }
}
