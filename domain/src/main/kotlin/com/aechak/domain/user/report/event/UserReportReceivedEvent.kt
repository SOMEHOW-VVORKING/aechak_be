package com.aechak.domain.user.report.event

import com.aechak.domain.support.DomainEvent
import com.aechak.domain.user.report.enums.ReportReasonCode
import java.time.LocalDateTime

/**
 * 사용자 신고가 접수됐다는 사실 이벤트
 * 트리거 : 운영팀 통지 메일 발송
 */
data class UserReportReceivedEvent(
    val reportId: Long,
    val targetUserId: Long,
    val reviewId: Long,
    val reasonCode: ReportReasonCode,
    val reasonText: String?,
    val createdAt: LocalDateTime,
) : DomainEvent
