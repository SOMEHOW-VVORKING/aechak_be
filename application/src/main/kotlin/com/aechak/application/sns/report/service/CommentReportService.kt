package com.aechak.application.sns.report.service

import com.aechak.application.sns.report.usecase.command.SubmitCommentReportCommand
import com.aechak.common.error.BusinessException
import com.aechak.domain.sns.comment.Comment
import com.aechak.domain.sns.error.SnsErrorCode
import com.aechak.domain.sns.report.CommentReport
import com.aechak.domain.sns.report.repository.CommentReportRepository
import com.aechak.domain.sns.report.repository.DuplicateCommentReportException
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service

@Service
class CommentReportService(
    private val commentReportRepository: CommentReportRepository,
    private val eventPublisher: ApplicationEventPublisher,
) {
    /** 게시물을 LAZY로 읽으므로 트랜잭션 안에서 부른다 */
    fun report(
        command: SubmitCommentReportCommand,
        comment: Comment,
    ) {
        val report =
            try {
                commentReportRepository.save(CommentReport.report(comment, command.reporterId, command.reasonCode, command.reasonText))
            } catch (e: DuplicateCommentReportException) {
                throw BusinessException(SnsErrorCode.SNS_COMMENT_ALREADY_REPORTED, e)
            }
        report.registerReceived(comment.publicId, comment.post.publicId)
        report.events.forEach { eventPublisher.publishEvent(it) }
        report.clearEvents()
    }
}
