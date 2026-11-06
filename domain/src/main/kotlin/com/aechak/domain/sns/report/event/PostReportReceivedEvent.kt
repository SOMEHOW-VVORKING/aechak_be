package com.aechak.domain.sns.report.event

import com.aechak.domain.sns.report.enums.PostReportReason
import com.aechak.domain.support.DomainEvent
import java.time.LocalDateTime

/**
 * SNS 게시물 신고가 접수됐다는 사실 이벤트
 * 트리거 : 운영팀 통지 메일 발송
 */
data class PostReportReceivedEvent(
    val reportId: Long,
    val postPublicId: String,
    val reasonCode: PostReportReason,
    val reasonText: String?,
    val createdAt: LocalDateTime,
) : DomainEvent
