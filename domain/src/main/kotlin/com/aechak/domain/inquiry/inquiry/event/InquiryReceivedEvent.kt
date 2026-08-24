package com.aechak.domain.inquiry.inquiry.event

import com.aechak.domain.inquiry.inquiry.enums.InquiryType
import com.aechak.domain.support.DomainEvent
import java.time.LocalDateTime

/**
 * 문의가 접수됐다는 사실 이벤트
 * 트리거 : 운영팀 통지 메일 발송
 */
data class InquiryReceivedEvent(
    val inquiryId: Long,
    val inquiryType: InquiryType,
    val userId: Long,
    val replyEmail: String,
    val content: String,
    val createdAt: LocalDateTime,
) : DomainEvent
