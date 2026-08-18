package com.aechak.application.inquiry.usecase.result

import com.aechak.domain.inquiry.inquiry.Inquiry
import com.aechak.domain.inquiry.inquiry.enums.InquiryStatus
import java.time.LocalDateTime

/** 접수 결과. 내부 id는 감추고 접수 확인용 최소 정보만 */
data class SubmitInquiryResult(
    val status: InquiryStatus,
    val createdAt: LocalDateTime,
) {
    companion object {
        fun from(inquiry: Inquiry): SubmitInquiryResult = SubmitInquiryResult(inquiry.status, inquiry.createdAt)
    }
}
