package com.aechak.api.inquiry.response

import com.aechak.application.inquiry.product.usecase.result.ProductInquiryItemResult
import com.aechak.domain.inquiry.product.enums.ProductInquiryCategory
import com.aechak.domain.inquiry.product.enums.ProductInquiryStatus
import java.time.LocalDateTime

/**
 * 상품 상세에 노출되는 문의 한 건.
 * readable이 거짓이면 제목·본문은 자리표시자이고 answer는 비어 있다 — 남의 비밀글이다.
 */
data class ProductInquiryItemResponse(
    val inquiryId: String,
    val category: ProductInquiryCategory,
    val title: String,
    val content: String,
    val authorMasked: String,
    val askedAt: LocalDateTime,
    val secret: Boolean,
    val readable: Boolean,
    val mine: Boolean,
    val status: ProductInquiryStatus,
    val answer: ProductInquiryAnswerResponse?,
) {
    companion object {
        fun from(result: ProductInquiryItemResult): ProductInquiryItemResponse =
            ProductInquiryItemResponse(
                inquiryId = result.inquiryPublicId,
                category = result.category,
                title = result.title,
                content = result.content,
                authorMasked = result.authorMasked,
                askedAt = result.askedAt,
                secret = result.secret,
                readable = result.readable,
                mine = result.mine,
                status = result.status,
                answer = result.answer?.let(ProductInquiryAnswerResponse::from),
            )
    }
}
