package com.aechak.application.inquiry.port.view

import com.aechak.domain.inquiry.inquiry.enums.InquiryStatus
import com.aechak.domain.inquiry.inquiry.enums.InquiryType
import java.time.LocalDateTime

/**
 * 운영자용 문의 목록 읽기 모델.
 * 답변 본문과 답변 수신 이메일은 싣지 않는다 — 목록은 처리 상태만 보이고 전문·연락처는 상세에서 읽는다.
 */
data class AdminInquiryView(
    val inquiryId: Long,
    val inquiryType: InquiryType,
    val status: InquiryStatus,
    val userId: Long,
    val content: String,
    val assignedAdminId: Long?,
    val answeredByAdminId: Long?,
    val resolvedAt: LocalDateTime?,
    val createdAt: LocalDateTime,
)
