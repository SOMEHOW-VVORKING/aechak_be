package com.aechak.application.inquiry.product.usecase.result

import com.aechak.domain.inquiry.product.ProductInquiry
import com.aechak.domain.inquiry.product.enums.ProductInquiryStatus
import java.time.LocalDateTime

data class AskProductInquiryResult(
    val inquiryPublicId: String,
    val status: ProductInquiryStatus,
    val answerDueAt: LocalDateTime,
) {
    companion object {
        fun from(inquiry: ProductInquiry): AskProductInquiryResult =
            AskProductInquiryResult(
                inquiryPublicId = inquiry.publicId,
                status = inquiry.status,
                answerDueAt = inquiry.answerDueAt,
            )
    }
}
