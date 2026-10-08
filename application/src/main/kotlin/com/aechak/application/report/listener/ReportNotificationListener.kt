package com.aechak.application.report.listener

import com.aechak.application.email.port.EmailMessage
import com.aechak.application.email.port.EmailSender
import com.aechak.application.report.port.ReportNotificationPolicy
import com.aechak.domain.product.report.event.ProductReportReceivedEvent
import com.aechak.domain.review.report.event.ReviewReportReceivedEvent
import com.aechak.domain.user.report.event.UserReportReceivedEvent
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import java.time.LocalDateTime

/** 상품, 리뷰, 사용자 신고 접수 이벤트를 수신해 운영팀에 통지 메일을 발송 */
@Component
class ReportNotificationListener(
    private val emailSender: EmailSender,
    private val notificationPolicy: ReportNotificationPolicy,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Async("reportNotificationTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handleProductReportReceived(event: ProductReportReceivedEvent) {
        notifyOps(
            targetType = "PRODUCT",
            reportId = event.reportId,
            target = "상품 publicId ${event.productPublicId}",
            reasonCode = event.reasonCode.name,
            reasonText = event.reasonText,
            createdAt = event.createdAt,
        )
    }

    @Async("reportNotificationTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handleReviewReportReceived(event: ReviewReportReceivedEvent) {
        notifyOps(
            targetType = "REVIEW",
            reportId = event.reportId,
            target = "리뷰 id ${event.reviewId}",
            reasonCode = event.reasonCode.name,
            reasonText = event.reasonText,
            createdAt = event.createdAt,
        )
    }

    @Async("reportNotificationTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handleUserReportReceived(event: UserReportReceivedEvent) {
        notifyOps(
            targetType = "USER",
            reportId = event.reportId,
            target = "사용자 id ${event.targetUserId} (리뷰 id ${event.reviewId} 작성자)",
            reasonCode = event.reasonCode.name,
            reasonText = event.reasonText,
            createdAt = event.createdAt,
        )
    }

    private fun notifyOps(
        targetType: String,
        reportId: Long,
        target: String,
        reasonCode: String,
        reasonText: String?,
        createdAt: LocalDateTime,
    ) {
        if (!notificationPolicy.enabled || notificationPolicy.recipients.isEmpty()) {
            log.debug("신고 통지 비활성으로 발송 생략 (targetType={}, reportId={})", targetType, reportId)
            return
        }
        try {
            emailSender.send(
                EmailMessage(
                    to = notificationPolicy.recipients,
                    replyTo = null,
                    subject = "[신고/$targetType] #$reportId",
                    body =
                        listOf(
                            "새 신고가 접수되었습니다.",
                            "",
                            "- 신고 ID: $reportId",
                            "- 대상: $target",
                            "- 사유: $reasonCode",
                            "- 접수 시각: $createdAt",
                            "",
                            "상세 사유:",
                            reasonText ?: "(없음)",
                        ).joinToString("\n"),
                ),
            )
        } catch (e: Exception) {
            log.error("신고 통지 메일 발송 실패, 수동 확인 대상 (targetType={}, reportId={})", targetType, reportId, e)
        }
    }
}
