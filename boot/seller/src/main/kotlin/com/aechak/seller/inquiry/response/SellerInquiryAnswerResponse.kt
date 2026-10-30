package com.aechak.seller.inquiry.response

import com.aechak.application.inquiry.product.usecase.result.ProductInquiryAnswerResult
import java.time.LocalDateTime

data class SellerInquiryAnswerResponse(
    val content: String,
    val answeredAt: LocalDateTime,
) {
    companion object {
        fun from(result: ProductInquiryAnswerResult): SellerInquiryAnswerResponse =
            SellerInquiryAnswerResponse(content = result.content, answeredAt = result.answeredAt)
    }
}
