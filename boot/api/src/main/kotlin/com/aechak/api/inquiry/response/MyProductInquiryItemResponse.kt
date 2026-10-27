package com.aechak.api.inquiry.response

import com.aechak.application.inquiry.product.usecase.result.MyProductInquiryItemResult
import com.aechak.domain.inquiry.product.enums.ProductInquiryCategory
import com.aechak.domain.inquiry.product.enums.ProductInquiryStatus
import java.time.LocalDateTime

/** 내가 등록한 문의 한 건. 본인 목록이라 비밀글도 본문 그대로 내려간다. */
data class MyProductInquiryItemResponse(
    val inquiryId: String,
    val productId: String,
    val productName: String,
    val category: ProductInquiryCategory,
    val title: String,
    val content: String,
    val secret: Boolean,
    val status: ProductInquiryStatus,
    val askedAt: LocalDateTime,
    val answerDueAt: LocalDateTime,
    val overdue: Boolean,
    val answer: ProductInquiryAnswerResponse?,
) {
    companion object {
        fun from(result: MyProductInquiryItemResult): MyProductInquiryItemResponse =
            MyProductInquiryItemResponse(
                inquiryId = result.inquiryPublicId,
                productId = result.productPublicId,
                productName = result.productName,
                category = result.category,
                title = result.title,
                content = result.content,
                secret = result.secret,
                status = result.status,
                askedAt = result.askedAt,
                answerDueAt = result.answerDueAt,
                overdue = result.overdue,
                answer = result.answer?.let(ProductInquiryAnswerResponse::from),
            )
    }
}
