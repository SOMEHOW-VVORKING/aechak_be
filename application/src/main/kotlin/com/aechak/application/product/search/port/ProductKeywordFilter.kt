package com.aechak.application.product.search.port

import java.math.BigDecimal

/**
 * 키워드 상품 검색 필터.
 *
 * - keyword: 정규화된 검색어(trim + NFC + lowercase)
 * - minPrice/maxPrice: 유효가격(할인 반영) 기준 범위. null이면 미적용
 * - minRating: 평균 별점 하한. 리뷰가 없어 averageRating이 null인 상품은 적용 시 제외
 * - categoryId: 중분류 필터. null이면 전체
 * - freeShipping: 셀러 기본 배송비가 0인 상품만
 * - excludeSoldOut: 품절 제외(판매중만)
 */
data class ProductKeywordFilter(
    val keyword: String,
    val minPrice: Long?,
    val maxPrice: Long?,
    val minRating: BigDecimal?,
    val categoryId: Long?,
    val freeShipping: Boolean,
    val excludeSoldOut: Boolean,
)
