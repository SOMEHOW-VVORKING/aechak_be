package com.aechak.application.product.product.port.view

/** 셀러 상품 옵션 조합의 읽기 모델 — 비활성 조합 포함, 재고 원값 */
data class SellerProductOptionView(
    val optionCombinationId: Long,
    val name: String,
    val additionalPrice: Long,
    val stockQuantity: Int,
    val isActive: Boolean,
)
