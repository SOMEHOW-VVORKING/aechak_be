package com.aechak.application.inquiry.facade

import com.aechak.application.inquiry.port.EmailMessage
import com.aechak.application.inquiry.port.EmailSender
import com.aechak.application.inquiry.port.InquiryNotificationPolicy
import com.aechak.application.inquiry.service.InquiryService
import com.aechak.application.inquiry.usecase.InquiryUseCase
import com.aechak.application.inquiry.usecase.command.SubmitInquiryCommand
import com.aechak.application.inquiry.usecase.result.SubmitInquiryResult
import com.aechak.domain.inquiry.inquiry.Inquiry
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

@Service
class InquiryFacade(
    private val inquiryService: InquiryService,
    private val emailSender: EmailSender,
    private val notificationPolicy: InquiryNotificationPolicy,
    transactionManager: PlatformTransactionManager,
) : InquiryUseCase {
    private val log = LoggerFactory.getLogger(javaClass)
    private val tx = TransactionTemplate(transactionManager)

    override fun submitInquiry(command: SubmitInquiryCommand): SubmitInquiryResult {
        val inquiry = requireNotNull(tx.execute { inquiryService.receive(command) })
        log.info("문의 접수 (inquiryId={}, type={})", inquiry.id, inquiry.inquiryType)
        notifyOperationsTeam(inquiry)
        return SubmitInquiryResult.from(inquiry)
    }

    private fun notifyOperationsTeam(inquiry: Inquiry) {
        if (!notificationPolicy.enabled || notificationPolicy.recipients.isEmpty()) {
            log.debug("문의 통지 비활성으로 발송 생략 (inquiryId={})", inquiry.id)
            return
        }
        // 발송 실패 시 접수를 따로 되돌리지 않음
        try {
            emailSender.send(
                EmailMessage(
                    to = notificationPolicy.recipients,
                    replyTo = inquiry.replyEmail,
                    subject = "[문의/${inquiry.inquiryType}] #${inquiry.id}",
                    body = buildBody(inquiry),
                ),
            )
        } catch (e: Exception) {
            log.error("문의 통지 메일 발송 실패, 수동 확인 대상 (inquiryId={}, type={})", inquiry.id, inquiry.inquiryType, e)
        }
    }

    private fun buildBody(inquiry: Inquiry): String =
        """
        새 앱 서비스 문의가 접수되었습니다.

        - 문의 ID: ${inquiry.id}
        - 유형: ${inquiry.inquiryType}
        - 작성자 userId: ${inquiry.userId}
        - 답변 이메일: ${inquiry.replyEmail}
        - 접수 시각: ${inquiry.createdAt}

        내용:
        ${inquiry.content}
        """.trimIndent()
}
