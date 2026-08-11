package com.aechak.application.product.search.usecase

import com.aechak.application.product.search.usecase.query.ProductKeywordSearchQuery
import com.aechak.application.product.usecase.result.ProductSummaryResult
import com.aechak.application.support.CursorPageResult

interface ProductKeywordSearchUseCase {
    fun searchProducts(
        query: ProductKeywordSearchQuery,
        userId: Long?, // userId는 카드별 isLiked(찜 여부) 판정용, 비로그인은 null
    ): CursorPageResult<ProductSummaryResult>
}
