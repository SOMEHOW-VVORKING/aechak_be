package com.aechak.application.product.usecase.result

import com.aechak.application.product.port.view.ProductCatalogView
import com.aechak.application.support.CursorPageResult
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
    val isLiked: Boolean,
) {
    companion object {
        fun from(
            view: ProductCatalogView,
            stats: ProductStats?,
            now: LocalDateTime,
            isLiked: Boolean,
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
                isLiked = isLiked,
            )
        }

        /** 카드 목록 페이지를 요약 결과 페이지로 변환 */
        fun fromPage(
            page: CursorPageResult<ProductCatalogView>,
            statsById: Map<Long, ProductStats>,
            likedIds: Set<Long>,
            now: LocalDateTime,
        ): CursorPageResult<ProductSummaryResult> =
            CursorPageResult(
                items = page.items.map { from(it, statsById[it.id], now, it.id in likedIds) },
                totalCount = page.totalCount,
                nextCursor = page.nextCursor,
                hasNext = page.hasNext,
            )
    }
}
