package com.aechak.api.inquiry.response

import com.aechak.application.inquiry.product.usecase.result.ProductInquiryAnswerResult
import java.time.LocalDateTime

data class ProductInquiryAnswerResponse(
    val content: String,
    val answeredAt: LocalDateTime,
) {
    companion object {
        fun from(result: ProductInquiryAnswerResult): ProductInquiryAnswerResponse =
            ProductInquiryAnswerResponse(content = result.content, answeredAt = result.answeredAt)
    }
}
