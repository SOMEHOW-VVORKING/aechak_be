package com.aechak.application.product.search.port

/** 키워드 상품 검색 정렬 어휘 */
enum class ProductKeywordSearchSort {
    POPULAR, // 인기순(리뷰 수 desc + id desc)
    PRICE_ASC, // 낮은가격순(유효가격 asc + id desc)
    LATEST, // 최신순(id desc)
}
