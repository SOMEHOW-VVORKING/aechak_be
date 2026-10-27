package com.aechak.seller.product.response

import com.aechak.application.product.product.usecase.result.SellerProductSummaryResult
import com.aechak.domain.product.product.enums.InspectionStatus
import com.aechak.domain.product.product.enums.SaleStatus
import java.time.LocalDateTime

data class SellerProductSummaryResponse(
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
        fun from(result: SellerProductSummaryResult): SellerProductSummaryResponse =
            SellerProductSummaryResponse(
                productId = result.productId,
                name = result.name,
                thumbnailImageKey = result.thumbnailImageKey,
                categoryName = result.categoryName,
                regularPrice = result.regularPrice,
                discountPrice = result.discountPrice,
                discountRate = result.discountRate,
                saleStatus = result.saleStatus,
                inspectionStatus = result.inspectionStatus,
                totalStock = result.totalStock,
                createdAt = result.createdAt,
            )
    }
}
