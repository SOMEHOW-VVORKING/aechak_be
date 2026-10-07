package com.aechak.api.inquiry.response

import com.aechak.application.inquiry.usecase.result.SubmitInquiryResult
import com.aechak.domain.inquiry.inquiry.enums.InquiryStatus
import java.time.LocalDateTime

/** 접수 확인 응답. 접수 완료 토스트에 필요한 최소 정보만 */
data class SubmitInquiryResponse(
    val status: InquiryStatus,
    val createdAt: LocalDateTime,
) {
    companion object {
        fun from(result: SubmitInquiryResult): SubmitInquiryResponse = SubmitInquiryResponse(result.status, result.createdAt)
    }
}
