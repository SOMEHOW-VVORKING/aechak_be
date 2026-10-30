package com.aechak.application.inquiry.product.usecase.result

import com.aechak.application.inquiry.product.port.view.ProductInquiryView
import com.aechak.domain.inquiry.product.enums.ProductInquiryCategory
import com.aechak.domain.inquiry.product.enums.ProductInquiryStatus
import java.time.LocalDateTime

/**
 * 셀러가 받은 문의 한 건. 답변 당사자라서 비밀글도 본문을 읽는다.
 * 기한 지남은 서버가 저장한 기한과 서버 시각으로 판정한 결과만 내려준다 — 화면이 날짜를 빼지 않는다.
 */
data class SellerProductInquiryItemResult(
    val inquiryPublicId: String,
    val productPublicId: String,
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
    val answer: ProductInquiryAnswerResult?,
) {
    companion object {
        fun from(
            view: ProductInquiryView,
            authorMasked: String,
            now: LocalDateTime,
        ): SellerProductInquiryItemResult =
            SellerProductInquiryItemResult(
                inquiryPublicId = view.publicId,
                productPublicId = view.productPublicId,
                productName = view.productName,
                category = view.category,
                title = view.title,
                content = view.content,
                authorMasked = authorMasked,
                secret = view.secret,
                status = view.status,
                askedAt = view.askedAt,
                answerDueAt = view.answerDueAt,
                overdue = view.status == ProductInquiryStatus.RECEIVED && view.answerDueAt.isBefore(now),
                answer = ProductInquiryAnswerResult.from(view),
            )
    }
}
