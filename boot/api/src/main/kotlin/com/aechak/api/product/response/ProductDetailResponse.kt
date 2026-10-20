package com.aechak.api.product.response

import com.aechak.application.product.product.usecase.result.ProductResult
import com.aechak.domain.product.product.enums.SaleStatus
import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema
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
    val saleStatus: SaleStatus,
    val shipping: ShippingResponse,
    val seller: SellerSummaryResponse,
    val review: ReviewSummaryResponse,
    val categories: List<CategoryResponse>,
    @get:JsonProperty("isLiked")
    val isLiked: Boolean,
    @get:JsonProperty("isPurchasable")
    val isPurchasable: Boolean,
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

    // 카테고리 트리 노드(CategoryResponse)와 단순 이름이 같아 문서 스키마가 서로 덮어쓰므로 이름을 따로 못박는다.
    @Schema(name = "ProductCategoryPathResponse")
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
                saleStatus = result.saleStatus,
                shipping = ShippingResponse(result.shipping.baseShippingFee, result.shipping.freeShippingThreshold),
                seller = SellerSummaryResponse(result.seller.storeName, result.seller.profileImageKey),
                review = ReviewSummaryResponse(result.review.reviewCount, result.review.averageRating),
                categories = result.categories.map { CategoryResponse(it.categoryId, it.name, it.depth) },
                isLiked = result.isLiked,
                isPurchasable = result.isPurchasable,
            )
    }
}
