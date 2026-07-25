package com.aechak.application.product.port.result

import com.aechak.domain.product.product.enums.ProductImageType

/** 상품 이미지 읽기 모델 */
data class ProductImageView(
    val imageType: ProductImageType,
    val storageKey: String,
    val sortOrder: Int,
)
