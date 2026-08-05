package com.aechak.application.product.search.usecase.query

/**
 * 키워드 상품 검색 입력
 */
data class ProductKeywordSearchQuery(
    val keyword: String,
    val cursor: String? = null,
    val size: Int = DEFAULT_SIZE,
) {
    init {
        require(keyword.isNotBlank()) { "검색어는 비어 있을 수 없습니다." }
        require(keyword.length <= MAX_LENGTH) { "검색어는 최대 $MAX_LENGTH 자입니다." }
        require(size in SIZE_RANGE) { "size는 $SIZE_RANGE 범위 안에 있어야 합니다." }
    }

    companion object {
        const val DEFAULT_SIZE = 20
        const val MAX_LENGTH = 100
        val SIZE_RANGE = 1..100
    }
}
