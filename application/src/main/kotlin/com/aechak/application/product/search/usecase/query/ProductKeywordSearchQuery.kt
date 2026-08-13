package com.aechak.application.product.search.usecase.query

import com.aechak.application.product.search.port.ProductKeywordSearchSort
import java.math.BigDecimal

/**
 * 키워드 상품 검색 입력
 */
data class ProductKeywordSearchQuery(
    val keyword: String,
    val sort: ProductKeywordSearchSort = ProductKeywordSearchSort.POPULAR,
    val minPrice: Long? = null,
    val maxPrice: Long? = null,
    val minRating: BigDecimal? = null,
    val categoryId: Long? = null,
    val freeShipping: Boolean = false,
    val excludeSoldOut: Boolean = false,
    val cursor: String? = null,
    val size: Int = DEFAULT_SIZE,
) {
    init {
        require(keyword.isNotBlank()) { "검색어는 비어 있을 수 없습니다." }
        require(keyword.length <= MAX_LENGTH) { "검색어는 최대 $MAX_LENGTH 자입니다." }
        require(size in SIZE_MIN..SIZE_MAX) { "size는 $SIZE_MIN~$SIZE_MAX 범위 안에 있어야 합니다." }
        require(minPrice == null || minPrice >= 0) { "minPrice는 0 이상이어야 합니다." }
        require(maxPrice == null || maxPrice >= 0) { "maxPrice는 0 이상이어야 합니다." }
        require(minPrice == null || maxPrice == null || minPrice <= maxPrice) { "minPrice는 maxPrice 이하여야 합니다." }
        require(
            minRating == null || (minRating in RATING_MIN..RATING_MAX),
        ) { "minRating은 $RATING_MIN~$RATING_MAX 범위여야 합니다." }
    }

    companion object {
        // size: Int 기본값, List.take()/size로 이어져 Int
        const val DEFAULT_SIZE = 20
        const val MAX_LENGTH = 100

        // @Range(min, max) 속성이 long이라 Long 유지, Int는 컴파일 불가
        const val SIZE_MIN = 1L
        const val SIZE_MAX = 100L
        val RATING_MIN: BigDecimal = BigDecimal.ZERO
        val RATING_MAX: BigDecimal = BigDecimal("5.0")
    }
}
