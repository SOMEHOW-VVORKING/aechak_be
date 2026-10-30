package com.aechak.application.inquiry.product.usecase.result

import com.aechak.application.inquiry.product.port.view.ProductInquiryView
import java.time.LocalDateTime

/** 셀러 답변. 답변이 없으면 이 객체 자체가 없다. */
data class ProductInquiryAnswerResult(
    val content: String,
    val answeredAt: LocalDateTime,
) {
    companion object {
        /** 답변 본문과 답변 시각이 둘 다 있을 때만 만든다 — 한쪽만 있는 행은 답변으로 치지 않는다. */
        fun from(view: ProductInquiryView): ProductInquiryAnswerResult? {
            val content = view.answerContent ?: return null
            val answeredAt = view.answeredAt ?: return null
            return ProductInquiryAnswerResult(content, answeredAt)
        }
    }
}
