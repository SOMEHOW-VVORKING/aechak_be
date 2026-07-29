package com.aechak.application.product.usecase.result

import com.aechak.application.product.port.view.ProductCatalogDetailView
import com.aechak.application.product.port.view.ProductImageView
import com.aechak.domain.product.product.enums.ProductImageType
import com.aechak.domain.product.product.enums.SaleStatus
import com.aechak.domain.product.stats.ProductStats
import java.math.BigDecimal
import java.time.LocalDateTime

/** 공개 상품 상세 결과 */
data class ProductResult(
    val productId: String,
    val name: String,
    val description: String?,
    val representativeImageKey: String?,
    val images: List<ImageResult>,
    val regularPrice: Long,
    val discountPrice: Long?,
    val discountRate: Int?,
    val saleStatus: SaleStatus,
    val shipping: ShippingResult,
    val seller: SellerSummaryResult,
    val review: ReviewSummaryResult,
    val categories: List<CategoryResult>,
    val isLiked: Boolean,
) {
    data class ImageResult(
        val imageType: ProductImageType,
        val storageKey: String,
        val sortOrder: Int,
    )

    data class ShippingResult(
        val baseShippingFee: Long,
        val freeShippingThreshold: Long?,
    )

    data class SellerSummaryResult(
        val storeName: String,
        val profileImageKey: String?,
    )

    data class ReviewSummaryResult(
        val reviewCount: Int,
        val averageRating: BigDecimal?,
    )

    /** 카테고리 경로 한 노드(대>중>소 순) */
    data class CategoryResult(
        val categoryId: Long,
        val name: String,
        val depth: Int,
    )

    companion object {
        fun from(
            view: ProductCatalogDetailView,
            images: List<ProductImageView>,
            stats: ProductStats?,
            isLiked: Boolean,
            now: LocalDateTime,
        ): ProductResult {
            val pricing = view.pricing()
            return ProductResult(
                productId = view.publicId,
                name = view.name,
                description = view.description,
                representativeImageKey = view.representativeImageKey,
                images = images.map { ImageResult(it.imageType, it.storageKey, it.sortOrder) },
                regularPrice = view.regularPrice,
                discountPrice = pricing.discountedPriceAt(now),
                discountRate = pricing.discountRateAt(now),
                saleStatus = view.saleStatus,
                shipping = ShippingResult(view.baseShippingFee, view.freeShippingThreshold),
                seller = SellerSummaryResult(view.sellerStoreName, view.sellerProfileImageKey),
                review = ReviewSummaryResult(stats?.reviewCount ?: 0, stats?.averageRating),
                categories = categoriesOf(view),
                isLiked = isLiked,
            )
        }

        private fun categoriesOf(view: ProductCatalogDetailView): List<CategoryResult> =
            buildList {
                view.grandParentCategoryId?.let {
                    add(CategoryResult(it, view.grandParentCategoryName!!, view.categoryDepth - 2))
                }
                view.parentCategoryId?.let {
                    add(CategoryResult(it, view.parentCategoryName!!, view.categoryDepth - 1))
                }
                add(CategoryResult(view.categoryId, view.categoryName, view.categoryDepth))
            }
    }
}
