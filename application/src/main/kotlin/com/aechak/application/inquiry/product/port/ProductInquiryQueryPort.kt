package com.aechak.application.inquiry.product.port

import com.aechak.application.inquiry.product.port.view.ProductInquiryView
import com.aechak.application.inquiry.product.port.view.SellerProductInquiryMetricsView
import java.time.LocalDateTime

/** 상품 문의 목록·집계 조회 포트. */
interface ProductInquiryQueryPort {
    fun findProductPage(condition: ProductInquiryPageCondition): List<ProductInquiryView>

    fun countProduct(productId: Long): Long

    fun findMyPage(condition: MyProductInquiryCondition): List<ProductInquiryView>

    fun countMine(condition: MyProductInquiryCondition): Long

    fun findSellerPage(condition: SellerProductInquiryCondition): List<ProductInquiryView>

    fun countSeller(condition: SellerProductInquiryCondition): Long

    /** 답변 대기·기한 지남·전체를 쿼리 한 번으로 센다. 기한 판정 기준 시각을 서버가 넘긴다. */
    fun countSellerMetrics(
        sellerId: Long,
        now: LocalDateTime,
    ): SellerProductInquiryMetricsView
}
