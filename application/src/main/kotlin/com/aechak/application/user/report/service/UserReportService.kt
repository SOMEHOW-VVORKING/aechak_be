package com.aechak.application.user.report.service

import com.aechak.application.user.report.usecase.command.SubmitUserReportCommand
import com.aechak.application.user.user.service.UserService
import com.aechak.common.error.BusinessException
import com.aechak.domain.user.error.UserErrorCode
import com.aechak.domain.user.report.repository.DuplicateUserReportException
import com.aechak.domain.user.report.repository.UserReportRepository
import com.aechak.domain.user.user.enums.UserStatus
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service

@Service
class UserReportService(
    private val userService: UserService,
    private val userReportRepository: UserReportRepository,
    private val eventPublisher: ApplicationEventPublisher,
) {
    fun report(
        command: SubmitUserReportCommand,
        targetUserId: Long,
    ) {
        val reporter = userService.getById(command.reporterId)
        val targetUser = userService.getById(targetUserId)
        if (targetUser.status == UserStatus.WITHDRAWN) {
            throw BusinessException(UserErrorCode.USER_NOT_FOUND)
        }
        val report =
            try {
                userReportRepository.save(command.toEntity(reporter, targetUser))
            } catch (e: DuplicateUserReportException) {
                throw BusinessException(UserErrorCode.USER_ALREADY_REPORTED, e)
            }
        report.registerReceived(command.reviewId)
        report.events.forEach { eventPublisher.publishEvent(it) }
        report.clearEvents()
    }
}
