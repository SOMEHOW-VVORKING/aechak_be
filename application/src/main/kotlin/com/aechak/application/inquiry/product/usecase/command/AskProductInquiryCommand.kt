package com.aechak.application.inquiry.product.usecase.command

import com.aechak.application.product.product.usecase.result.ProductRefResult
import com.aechak.domain.inquiry.product.ProductInquiry
import com.aechak.domain.inquiry.product.enums.ProductInquiryCategory
import java.time.LocalDateTime

data class AskProductInquiryCommand(
    val authorUserId: Long,
    val productPublicId: String,
    val category: ProductInquiryCategory,
    val title: String,
    val content: String,
    val secret: Boolean,
) {
    /** 상품명·셀러는 접수 시점 값을 복사한다 — 상품이 내려가도 문의가 고아가 되지 않는다. */
    fun toEntity(
        product: ProductRefResult,
        now: LocalDateTime,
    ): ProductInquiry =
        ProductInquiry.ask(
            productId = product.productId,
            productPublicId = product.productPublicId,
            sellerId = product.sellerId,
            authorUserId = authorUserId,
            productNameSnapshot = product.name,
            category = category,
            title = title,
            content = content,
            secret = secret,
            now = now,
        )
}
