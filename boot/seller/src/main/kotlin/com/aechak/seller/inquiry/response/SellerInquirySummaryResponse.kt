package com.aechak.seller.inquiry.response

import com.aechak.application.inquiry.product.usecase.result.SellerProductInquiryItemResult
import com.aechak.domain.inquiry.product.enums.ProductInquiryCategory
import com.aechak.domain.inquiry.product.enums.ProductInquiryStatus
import java.time.LocalDateTime

/** 셀러가 받은 문의 한 건. 답변 당사자라서 비밀글도 본문이 내려간다. */
data class SellerInquirySummaryResponse(
    val inquiryId: String,
    val productId: String,
    val productName: String,
    val category: ProductInquiryCategory,
    val title: String,
    val content: String,
    val authorMasked: String,
    val secret: Boolean,
    val status: ProductInquiryStatus,
    val askedAt: LocalDateTime,
    val answerDueAt: LocalDateTime,
    val overdue: Boolean,
    val answer: SellerInquiryAnswerResponse?,
) {
    companion object {
        fun from(result: SellerProductInquiryItemResult): SellerInquirySummaryResponse =
            SellerInquirySummaryResponse(
                inquiryId = result.inquiryPublicId,
                productId = result.productPublicId,
                productName = result.productName,
                category = result.category,
                title = result.title,
                content = result.content,
                authorMasked = result.authorMasked,
                secret = result.secret,
                status = result.status,
                askedAt = result.askedAt,
                answerDueAt = result.answerDueAt,
                overdue = result.overdue,
                answer = result.answer?.let(SellerInquiryAnswerResponse::from),
            )
    }
}
