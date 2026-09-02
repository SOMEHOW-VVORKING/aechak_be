package com.aechak.application.product.product.usecase.query

import com.aechak.application.product.product.port.ProductCatalogSort
import com.aechak.application.support.CursorPageSize

/** 옵셔널 필터, 페이징을 포함한 상품 목록 조회 입력 */
data class ProductSearchQuery(
    val categoryId: Long? = null,
    val sort: ProductCatalogSort = ProductCatalogSort.LATEST,
    val cursor: String? = null,
    val size: Int = CursorPageSize.DEFAULT,
) {
    init {
        require(size in CursorPageSize.MIN..CursorPageSize.MAX) { "size는 ${CursorPageSize.MIN}~${CursorPageSize.MAX} 범위 안에 있어야 합니다." }
    }
}
