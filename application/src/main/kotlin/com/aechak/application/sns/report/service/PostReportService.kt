package com.aechak.application.sns.report.service

import com.aechak.application.sns.report.usecase.command.SubmitPostReportCommand
import com.aechak.common.error.BusinessException
import com.aechak.domain.sns.error.SnsErrorCode
import com.aechak.domain.sns.post.Post
import com.aechak.domain.sns.report.PostReport
import com.aechak.domain.sns.report.repository.DuplicatePostReportException
import com.aechak.domain.sns.report.repository.PostReportRepository
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service

@Service
class PostReportService(
    private val postReportRepository: PostReportRepository,
    private val eventPublisher: ApplicationEventPublisher,
) {
    fun report(
        command: SubmitPostReportCommand,
        post: Post,
    ) {
        val report =
            try {
                postReportRepository.save(PostReport.report(post, command.reporterId, command.reasonCode, command.reasonText))
            } catch (e: DuplicatePostReportException) {
                throw BusinessException(SnsErrorCode.SNS_POST_ALREADY_REPORTED, e)
            }
        report.registerReceived(post.publicId)
        report.events.forEach { eventPublisher.publishEvent(it) }
        report.clearEvents()
    }
}
