package com.aechak.application.inquiry.product.usecase.result

import com.aechak.application.inquiry.product.port.view.ProductInquiryView
import com.aechak.domain.inquiry.product.enums.ProductInquiryCategory
import com.aechak.domain.inquiry.product.enums.ProductInquiryStatus
import java.time.LocalDateTime

/** 내가 등록한 문의 한 건. 본인 목록이므로 비밀글도 본문 그대로 본다. */
data class MyProductInquiryItemResult(
    val inquiryPublicId: String,
    val productPublicId: String,
    val productName: String,
    val category: ProductInquiryCategory,
    val title: String,
    val content: String,
    val secret: Boolean,
    val status: ProductInquiryStatus,
    val askedAt: LocalDateTime,
    val answerDueAt: LocalDateTime,
    val overdue: Boolean,
    val answer: ProductInquiryAnswerResult?,
) {
    companion object {
        fun from(
            view: ProductInquiryView,
            now: LocalDateTime,
        ): MyProductInquiryItemResult =
            MyProductInquiryItemResult(
                inquiryPublicId = view.publicId,
                productPublicId = view.productPublicId,
                productName = view.productName,
                category = view.category,
                title = view.title,
                content = view.content,
                secret = view.secret,
                status = view.status,
                askedAt = view.askedAt,
                answerDueAt = view.answerDueAt,
                overdue = view.status == ProductInquiryStatus.RECEIVED && view.answerDueAt.isBefore(now),
                answer = ProductInquiryAnswerResult.from(view),
            )
    }
}
