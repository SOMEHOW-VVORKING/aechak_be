package com.aechak.application.product.usecase.query

import com.aechak.application.product.port.ProductCatalogSort

/** 옵셔널 필터, 페이징을 포함한 상품 목록 조회 입력 */
data class ProductSearchQuery(
    val categoryId: Long? = null,
    val sort: ProductCatalogSort = ProductCatalogSort.LATEST,
    val cursor: String? = null,
    val size: Int = 20,
) {
    init {
        require(size in SIZE_RANGE) { "size는 $SIZE_RANGE 범위 안에 있어야 합니다." }
    }

    companion object {
        val SIZE_RANGE = 1..100
    }
}
