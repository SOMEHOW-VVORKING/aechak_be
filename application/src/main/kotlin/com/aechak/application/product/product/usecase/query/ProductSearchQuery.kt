package com.aechak.application.product.product.usecase.query

import com.aechak.application.product.product.port.ProductCatalogSort

/** 옵셔널 필터, 페이징을 포함한 상품 목록 조회 입력 */
data class ProductSearchQuery(
    val categoryId: Long? = null,
    val sort: ProductCatalogSort = ProductCatalogSort.LATEST,
    val cursor: String? = null,
    val size: Int = DEFAULT_SIZE,
) {
    init {
        require(size in SIZE_MIN..SIZE_MAX) { "size는 $SIZE_MIN~$SIZE_MAX 범위 안에 있어야 합니다." }
    }

    companion object {
        // size: Int 기본값, List.take()/size로 이어져 Int
        const val DEFAULT_SIZE = 20

        // @Range(min, max) 속성이 long이라 Long 유지, Int는 컴파일 불가
        const val SIZE_MIN = 1L
        const val SIZE_MAX = 100L
    }
}
