package com.aechak.application.product.search.port

import com.aechak.application.product.product.port.view.ProductCatalogView

/** 첫 페이지 목록과 같은 조건의 정확한 총개수 */
data class ProductKeywordSearchFirstPage(
    val fetched: List<ProductCatalogView>,
    val totalCount: Long,
)
