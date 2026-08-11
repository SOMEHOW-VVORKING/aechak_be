package com.aechak.application.product.search.usecase

import com.aechak.application.product.search.usecase.query.ProductKeywordSearchQuery
import com.aechak.application.product.usecase.result.ProductSummaryResult
import com.aechak.application.support.CursorPageResult

interface ProductKeywordSearchUseCase {
    fun searchProducts(
        query: ProductKeywordSearchQuery,
        searcherId: Long?,
    ): CursorPageResult<ProductSummaryResult>
}
