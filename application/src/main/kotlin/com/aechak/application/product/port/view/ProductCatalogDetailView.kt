package com.aechak.application.product.port.view

import com.aechak.domain.product.product.ProductPricing
import com.aechak.domain.product.product.enums.SaleStatus
import java.time.LocalDateTime

/** 카탈로그 상세 조회의 읽기 모델(상품/셀러(배송 정책 포함)/카테고리 체인)) */
data class ProductCatalogDetailView(
    val id: Long,
    val publicId: String,
    val name: String,
    val description: String?,
    val representativeImageKey: String?,
    val regularPrice: Long,
    val discountPrice: Long?,
    val discountStartAt: LocalDateTime?,
    val discountEndAt: LocalDateTime?,
    val saleStatus: SaleStatus,
    val sellerStoreName: String,
    val sellerProfileImageKey: String?,
    val baseShippingFee: Long,
    val freeShippingThreshold: Long?,
    val categoryId: Long,
    val categoryName: String,
    val categoryDepth: Int,
    val parentCategoryId: Long?,
    val parentCategoryName: String?,
    val grandParentCategoryId: Long?,
    val grandParentCategoryName: String?,
) {
    fun pricing(): ProductPricing =
        ProductPricing(
            regularPrice = regularPrice,
            discountPrice = discountPrice,
            discountStartAt = discountStartAt,
            discountEndAt = discountEndAt,
        )
}
