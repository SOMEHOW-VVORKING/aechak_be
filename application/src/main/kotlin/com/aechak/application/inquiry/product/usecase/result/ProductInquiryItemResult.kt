package com.aechak.application.inquiry.product.usecase.result

import com.aechak.application.inquiry.product.port.view.ProductInquiryView
import com.aechak.domain.inquiry.product.ProductInquiry
import com.aechak.domain.inquiry.product.enums.ProductInquiryCategory
import com.aechak.domain.inquiry.product.enums.ProductInquiryStatus
import java.time.LocalDateTime

/**
 * 상품 상세에 노출되는 문의 한 건.
 * 비밀글은 작성자와 해당 상품 셀러만 본문을 읽는다 — 그 외 조회자에게는 제목·본문·답변을
 * 자리표시자로 바꿔 내려준다. 행 자체는 남겨서 문의 수가 거짓이 되지 않게 한다.
 */
data class ProductInquiryItemResult(
    val inquiryPublicId: String,
    val category: ProductInquiryCategory,
    val title: String,
    val content: String,
    val authorMasked: String,
    val askedAt: LocalDateTime,
    val secret: Boolean,
    val readable: Boolean,
    val mine: Boolean,
    val status: ProductInquiryStatus,
    val answer: ProductInquiryAnswerResult?,
) {
    companion object {
        fun from(
            view: ProductInquiryView,
            authorMasked: String,
            viewerId: Long,
        ): ProductInquiryItemResult {
            val readable = !view.secret || viewerId == view.authorUserId || viewerId == view.sellerId
            return ProductInquiryItemResult(
                inquiryPublicId = view.publicId,
                category = view.category,
                title = if (readable) view.title else ProductInquiry.SECRET_PLACEHOLDER,
                content = if (readable) view.content else ProductInquiry.SECRET_PLACEHOLDER,
                authorMasked = authorMasked,
                askedAt = view.askedAt,
                secret = view.secret,
                readable = readable,
                mine = viewerId == view.authorUserId,
                status = view.status,
                answer = if (readable) ProductInquiryAnswerResult.from(view) else null,
            )
        }
    }
}
