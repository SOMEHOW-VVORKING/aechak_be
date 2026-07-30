package com.aechak.api.product.response

import com.aechak.application.product.usecase.result.ProductResult
import com.fasterxml.jackson.annotation.JsonProperty
import java.math.BigDecimal

data class ProductDetailResponse(
    val productId: String,
    val name: String,
    val description: String?,
    val representativeImageKey: String?,
    val images: List<ImageResponse>,
    val regularPrice: Long,
    val discountPrice: Long?,
    val discountRate: Int?,
    val saleStatus: String,
    val shipping: ShippingResponse,
    val seller: SellerSummaryResponse,
    val review: ReviewSummaryResponse,
    val categories: List<CategoryResponse>,
    @get:JsonProperty("isLiked")
    val isLiked: Boolean,
) {
    data class ImageResponse(
        val imageType: String,
        val storageKey: String,
        val sortOrder: Int,
    )

    data class ShippingResponse(
        val baseShippingFee: Long,
        val freeShippingThreshold: Long?,
    )

    data class SellerSummaryResponse(
        val storeName: String,
        val profileImageKey: String?,
    )

    data class ReviewSummaryResponse(
        val reviewCount: Int,
        val averageRating: BigDecimal?,
    )

    data class CategoryResponse(
        val categoryId: Long,
        val name: String,
        val depth: Int,
    )

    companion object {
        fun from(result: ProductResult): ProductDetailResponse =
            ProductDetailResponse(
                productId = result.productId,
                name = result.name,
                description = result.description,
                representativeImageKey = result.representativeImageKey,
                images = result.images.map { ImageResponse(it.imageType.name, it.storageKey, it.sortOrder) },
                regularPrice = result.regularPrice,
                discountPrice = result.discountPrice,
                discountRate = result.discountRate,
                saleStatus = result.saleStatus.name,
                shipping = ShippingResponse(result.shipping.baseShippingFee, result.shipping.freeShippingThreshold),
                seller = SellerSummaryResponse(result.seller.storeName, result.seller.profileImageKey),
                review = ReviewSummaryResponse(result.review.reviewCount, result.review.averageRating),
                categories = result.categories.map { CategoryResponse(it.categoryId, it.name, it.depth) },
                isLiked = result.isLiked,
            )
    }
}
