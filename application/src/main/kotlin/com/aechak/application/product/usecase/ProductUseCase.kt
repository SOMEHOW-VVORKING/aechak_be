package com.aechak.application.product.usecase

import com.aechak.application.product.usecase.query.ProductSearchQuery
import com.aechak.application.product.usecase.result.ProductSummaryResult
import com.aechak.application.support.CursorPageResult

interface ProductUseCase {
    /** 상품 목록 조회 — 카테고리(중분류) 필터 + 정렬 + 커서 페이지네이션 */
    fun getProducts(query: ProductSearchQuery): CursorPageResult<ProductSummaryResult>
}
