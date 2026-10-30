package com.aechak.api.inquiry.response

import com.aechak.application.inquiry.product.usecase.result.AskProductInquiryResult
import com.aechak.domain.inquiry.product.enums.ProductInquiryStatus
import java.time.LocalDateTime

/** 등록 확인 응답. 답변 기한은 서버가 계산한 값을 그대로 내려준다. */
data class AskProductInquiryResponse(
    val inquiryId: String,
    val status: ProductInquiryStatus,
    val answerDueAt: LocalDateTime,
) {
    companion object {
        fun from(result: AskProductInquiryResult): AskProductInquiryResponse =
            AskProductInquiryResponse(
                inquiryId = result.inquiryPublicId,
                status = result.status,
                answerDueAt = result.answerDueAt,
            )
    }
}
