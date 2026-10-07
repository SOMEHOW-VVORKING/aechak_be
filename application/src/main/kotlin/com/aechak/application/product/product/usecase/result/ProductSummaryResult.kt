package com.aechak.application.product.product.usecase.result

import com.aechak.application.product.product.port.view.ProductCatalogView
import com.aechak.application.support.CursorPageResult
import com.aechak.domain.product.product.enums.SaleStatus
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
    val isViewable: Boolean,
    val isPurchasable: Boolean,
    val isLiked: Boolean,
) {
    companion object {
        fun from(
            view: ProductCatalogView,
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
                averageRating = view.averageRating,
                reviewCount = view.reviewCount,
                isViewable = view.isViewable,
                // 상세 진입이 막힌 상품은 구매도 불가하므로 isViewable을 함께 본다
                isPurchasable = view.isViewable && view.saleStatus.canPurchase(),
                isLiked = isLiked,
            )
        }

        fun fromPage(
            page: CursorPageResult<ProductCatalogView>,
            likedIds: Set<Long>,
            now: LocalDateTime,
        ): CursorPageResult<ProductSummaryResult> =
            CursorPageResult(
                items = page.items.map { from(it, now, it.id in likedIds) },
                totalCount = page.totalCount,
                nextCursor = page.nextCursor,
                hasNext = page.hasNext,
            )
    }
}
