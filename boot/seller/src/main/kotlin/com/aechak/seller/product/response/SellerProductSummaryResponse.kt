package com.aechak.seller.product.response

import com.aechak.application.product.product.usecase.result.SellerProductSummaryResult
import java.time.LocalDateTime

data class SellerProductSummaryResponse(
    val productId: String,
    val name: String,
    val thumbnailImageKey: String?,
    val categoryName: String,
    val regularPrice: Long,
    val discountPrice: Long?,
    val discountRate: Int?,
    val saleStatus: String,
    val inspectionStatus: String,
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
                saleStatus = result.saleStatus.name,
                inspectionStatus = result.inspectionStatus.name,
                totalStock = result.totalStock,
                createdAt = result.createdAt,
            )
    }
}
