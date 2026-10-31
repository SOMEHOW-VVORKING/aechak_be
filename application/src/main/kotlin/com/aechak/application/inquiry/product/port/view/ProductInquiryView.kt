package com.aechak.application.inquiry.product.port.view

import com.aechak.domain.inquiry.product.enums.ProductInquiryCategory
import com.aechak.domain.inquiry.product.enums.ProductInquiryStatus
import java.time.LocalDateTime

/**
 * 상품 문의 한 건의 읽기 모델. 구매자 목록·상품 상세·셀러 목록이 같은 투영을 공유한다 —
 * 세 화면이 같은 행을 다르게 세거나 다르게 가리지 않게 하려면 재료가 하나여야 한다.
 */
data class ProductInquiryView(
    val id: Long,
    val publicId: String,
    val productId: Long,
    val productPublicId: String,
    val sellerId: Long,
    val authorUserId: Long,
    val productName: String,
    val category: ProductInquiryCategory,
    val title: String,
    val content: String,
    val secret: Boolean,
    val status: ProductInquiryStatus,
    val answerDueAt: LocalDateTime,
    val answeredAt: LocalDateTime?,
    val askedAt: LocalDateTime,
    val answerContent: String?,
)
