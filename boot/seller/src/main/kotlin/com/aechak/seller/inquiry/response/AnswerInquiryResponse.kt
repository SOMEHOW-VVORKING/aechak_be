package com.aechak.seller.inquiry.response

import com.aechak.application.inquiry.product.usecase.result.AnswerProductInquiryResult
import com.aechak.domain.inquiry.product.enums.ProductInquiryStatus
import java.time.LocalDateTime

data class AnswerInquiryResponse(
    val inquiryId: String,
    val status: ProductInquiryStatus,
    val answeredAt: LocalDateTime,
) {
    companion object {
        fun from(result: AnswerProductInquiryResult): AnswerInquiryResponse =
            AnswerInquiryResponse(
                inquiryId = result.inquiryPublicId,
                status = result.status,
                answeredAt = result.answeredAt,
            )
    }
}
