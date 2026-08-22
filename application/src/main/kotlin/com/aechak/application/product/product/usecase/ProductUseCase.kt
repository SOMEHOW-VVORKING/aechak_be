package com.aechak.application.product.product.usecase

import com.aechak.application.product.product.usecase.query.ProductSearchQuery
import com.aechak.application.product.product.usecase.result.ProductOptionsResult
import com.aechak.application.product.product.usecase.result.ProductResult
import com.aechak.application.product.product.usecase.result.ProductSummaryResult
import com.aechak.application.support.CursorPageResult

interface ProductUseCase {
    /** 상품 목록 조회 — 카테고리(중분류) 필터 + 정렬 + 커서 페이지네이션 */
    fun getProducts(
        query: ProductSearchQuery,
        userId: Long?,
    ): CursorPageResult<ProductSummaryResult>

    /** 상품 상세 조회 */
    fun getProduct(
        publicId: String,
        userId: Long?, // userId는 isLiked(찜 여부) 판정용
    ): ProductResult

    /** 상품 옵션 조회 */
    fun getProductOptions(publicId: String): ProductOptionsResult
}
