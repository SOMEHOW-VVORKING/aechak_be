package com.aechak.domain.inquiry.inquiry.event

import com.aechak.domain.inquiry.inquiry.enums.InquiryType
import com.aechak.domain.support.DomainEvent
import java.time.LocalDateTime

/**
 * 운영자가 문의에 답변했다는 사실 이벤트.
 * 현재 구독자는 없다 — 문의자 통지는 발송 어댑터 조립·수신 정책이 정해진 뒤 리스너로 붙인다.
 */
data class InquiryAnsweredEvent(
    val inquiryId: Long,
    val inquiryType: InquiryType,
    val userId: Long,
    val replyEmail: String,
    val answer: String,
    val answeredByAdminId: Long,
    val answeredAt: LocalDateTime,
) : DomainEvent
