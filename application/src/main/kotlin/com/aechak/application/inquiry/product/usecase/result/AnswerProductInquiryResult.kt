package com.aechak.application.inquiry.product.usecase.result

import com.aechak.domain.inquiry.product.ProductInquiry
import com.aechak.domain.inquiry.product.enums.ProductInquiryStatus
import java.time.LocalDateTime

data class AnswerProductInquiryResult(
    val inquiryPublicId: String,
    val status: ProductInquiryStatus,
    val answeredAt: LocalDateTime,
) {
    companion object {
        fun from(inquiry: ProductInquiry): AnswerProductInquiryResult =
            AnswerProductInquiryResult(
                inquiryPublicId = inquiry.publicId,
                status = inquiry.status,
                answeredAt = requireNotNull(inquiry.answeredAt) { "답변 등록 직후에는 답변 시각이 있어야 합니다." },
            )
    }
}
