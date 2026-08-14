package com.aechak.api.product.response

import com.aechak.application.product.product.usecase.result.ProductSummaryResult
import com.fasterxml.jackson.annotation.JsonProperty
import java.math.BigDecimal

data class ProductSummaryResponse(
    val productId: String,
    val name: String,
    val sellerName: String,
    val thumbnailImageKey: String?,
    val regularPrice: Long,
    val discountPrice: Long?,
    val discountRate: Int?,
    val saleStatus: String,
    val averageRating: BigDecimal?,
    val reviewCount: Int,
    @get:JsonProperty("isViewable")
    val isViewable: Boolean,
    @get:JsonProperty("isPurchasable")
    val isPurchasable: Boolean,
) {
    companion object {
        fun from(result: ProductSummaryResult): ProductSummaryResponse =
            ProductSummaryResponse(
                productId = result.productId,
                name = result.name,
                sellerName = result.sellerName,
                thumbnailImageKey = result.thumbnailImageKey,
                regularPrice = result.regularPrice,
                discountPrice = result.discountPrice,
                discountRate = result.discountRate,
                saleStatus = result.saleStatus.name,
                averageRating = result.averageRating,
                reviewCount = result.reviewCount,
                isViewable = result.isViewable,
                isPurchasable = result.isPurchasable,
            )
    }
}
