package com.aechak.application.product.port.view

import com.aechak.domain.product.product.ProductPricing
import com.aechak.domain.product.product.enums.InspectionStatus
import com.aechak.domain.product.product.enums.SaleStatus
import java.time.LocalDateTime

/**
 * 셀러 상품 목록의 읽기 모델.
 *
 * - totalStock: SQL이 합산한 활성 옵션 조합의 재고 합 — 판매 상태가 아니라 재고 원천 데이터 기준
 * - 할인 원본 필드: 응답 생성 시 현재 시각으로 표시가·할인율을 다시 계산([pricing])
 */
data class SellerProductView(
    val id: Long,
    val publicId: String,
    val name: String,
    val representativeImageKey: String?,
    val categoryName: String,
    val regularPrice: Long,
    val discountPrice: Long?,
    val discountStartAt: LocalDateTime?,
    val discountEndAt: LocalDateTime?,
    val saleStatus: SaleStatus,
    val inspectionStatus: InspectionStatus,
    val totalStock: Long,
    val createdAt: LocalDateTime,
) {
    fun pricing(): ProductPricing =
        ProductPricing(
            regularPrice = regularPrice,
            discountPrice = discountPrice,
            discountStartAt = discountStartAt,
            discountEndAt = discountEndAt,
        )
}
