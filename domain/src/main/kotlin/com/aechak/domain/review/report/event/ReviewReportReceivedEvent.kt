package com.aechak.domain.review.report.event

import com.aechak.domain.review.report.enums.ReviewReportReason
import com.aechak.domain.support.DomainEvent
import java.time.LocalDateTime

/**
 * 리뷰 신고가 접수됐다는 사실 이벤트
 * 트리거 : 운영팀 통지 메일 발송
 */
data class ReviewReportReceivedEvent(
    val reportId: Long,
    val reviewId: Long,
    val reasonCode: ReviewReportReason,
    val reasonText: String?,
    val createdAt: LocalDateTime,
) : DomainEvent
