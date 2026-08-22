package com.aechak.application.product.product.port

/** 공개 카탈로그 목록 정렬 어휘 */
enum class ProductCatalogSort {
    LATEST, // 신상품순(id desc)
    PRICE_ASC, // 낮은가격순(유효가격 asc + id desc)
}
