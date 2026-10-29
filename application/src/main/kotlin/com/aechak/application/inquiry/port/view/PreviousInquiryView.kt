package com.aechak.application.inquiry.port.view

import com.aechak.domain.inquiry.inquiry.enums.InquiryStatus
import com.aechak.domain.inquiry.inquiry.enums.InquiryType
import java.time.LocalDateTime

/** 같은 작성자의 과거 문의 읽기 모델 — 목록 표시에 필요한 최소 필드만. */
data class PreviousInquiryView(
    val inquiryId: Long,
    val inquiryType: InquiryType,
    val status: InquiryStatus,
    val createdAt: LocalDateTime,
)
