package com.aechak.application.product.product.usecase.result

import com.aechak.application.product.product.port.view.SellerProductView
import com.aechak.domain.product.product.enums.InspectionStatus
import com.aechak.domain.product.product.enums.SaleStatus
import java.time.LocalDateTime

/** 셀러 상품 목록 요약 — 표시가·할인율은 조회 시각으로 계산, 재고는 활성 조합 합 */
data class SellerProductSummaryResult(
    val productId: String,
    val name: String,
    val thumbnailImageKey: String?,
    val categoryName: String,
    val regularPrice: Long,
    val discountPrice: Long?,
    val discountRate: Int?,
    val saleStatus: SaleStatus,
    val inspectionStatus: InspectionStatus,
    val totalStock: Long,
    val createdAt: LocalDateTime,
) {
    companion object {
        fun from(
            view: SellerProductView,
            now: LocalDateTime,
        ): SellerProductSummaryResult {
            val pricing = view.pricing()
            return SellerProductSummaryResult(
                productId = view.publicId,
                name = view.name,
                thumbnailImageKey = view.representativeImageKey,
                categoryName = view.categoryName,
                regularPrice = view.regularPrice,
                discountPrice = pricing.discountedPriceAt(now),
                discountRate = pricing.discountRateAt(now),
                saleStatus = view.saleStatus,
                inspectionStatus = view.inspectionStatus,
                totalStock = view.totalStock,
                createdAt = view.createdAt,
            )
        }
    }
}
