package com.aechak.application.inquiry.listener

import com.aechak.application.email.port.EmailMessage
import com.aechak.application.email.port.EmailSender
import com.aechak.application.inquiry.port.InquiryNotificationPolicy
import com.aechak.domain.inquiry.inquiry.event.InquiryReceivedEvent
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/** 문의 접수 이벤트를 수신해 운영팀에 통지 메일을 발송 */
@Component
class InquiryNotificationListener(
    private val emailSender: EmailSender,
    private val notificationPolicy: InquiryNotificationPolicy,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Async("inquiryNotificationTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handleInquiryReceived(event: InquiryReceivedEvent) {
        if (!notificationPolicy.enabled || notificationPolicy.recipients.isEmpty()) {
            log.debug("문의 통지 비활성으로 발송 생략 (inquiryId={})", event.inquiryId)
            return
        }
        try {
            emailSender.send(
                EmailMessage(
                    to = notificationPolicy.recipients,
                    replyTo = event.replyEmail,
                    subject = "[문의/${event.inquiryType}] #${event.inquiryId}",
                    body = buildBody(event),
                ),
            )
        } catch (e: Exception) {
            log.error("문의 통지 메일 발송 실패, 수동 확인 대상 (inquiryId={}, inquiryType={})", event.inquiryId, event.inquiryType, e)
        }
    }

    private fun buildBody(event: InquiryReceivedEvent): String =
        """
        새 앱 서비스 문의가 접수되었습니다.

        - 문의 ID: ${event.inquiryId}
        - 유형: ${event.inquiryType}
        - 작성자 userId: ${event.userId}
        - 답변 이메일: ${event.replyEmail}
        - 접수 시각: ${event.createdAt}

        내용:
        ${event.content}
        """.trimIndent()
}
