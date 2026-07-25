package com.aechak.application.product.usecase.result

import com.aechak.application.product.port.result.ProductCatalogView
import com.aechak.domain.product.product.enums.SaleStatus
import com.aechak.domain.product.stats.ProductStats
import java.math.BigDecimal
import java.time.LocalDateTime

data class ProductSummaryResult(
    val productId: String,
    val name: String,
    val sellerName: String,
    val thumbnailImageKey: String?,
    val regularPrice: Long,
    val discountPrice: Long?,
    val discountRate: Int?,
    val saleStatus: SaleStatus,
    val averageRating: BigDecimal?,
    val reviewCount: Int,
) {
    companion object {
        fun from(
            view: ProductCatalogView,
            stats: ProductStats?,
            now: LocalDateTime,
        ): ProductSummaryResult {
            val pricing = view.pricing()
            return ProductSummaryResult(
                productId = view.publicId,
                name = view.name,
                sellerName = view.sellerName,
                thumbnailImageKey = view.representativeImageKey,
                regularPrice = view.regularPrice,
                discountPrice = pricing.discountedPriceAt(now),
                discountRate = pricing.discountRateAt(now),
                saleStatus = view.saleStatus,
                averageRating = stats?.averageRating,
                reviewCount = stats?.reviewCount ?: 0,
            )
        }
    }
}
