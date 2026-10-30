package com.aechak.application.product.product.usecase

import com.aechak.application.product.product.usecase.query.ProductSearchQuery
import com.aechak.application.product.product.usecase.result.ProductCurationResult
import com.aechak.application.product.product.usecase.result.ProductOptionsResult
import com.aechak.application.product.product.usecase.result.ProductRefResult
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

    /** 공개 상품의 내부 id 해석 — 없거나 미노출이면 404. */
    fun getVisibleProductId(publicId: String): Long

    /**
     * 공개 상품의 식별 정보(내부 id·셀러·상품명) 해석 — 없거나 미노출이면 404.
     * 상품명·셀러를 자기 행에 복사해 두는 타 BC(상품 문의)를 위한 최소 조회다.
     */
    fun getVisibleProductRef(publicId: String): ProductRefResult

    /** 큐레이션 상품 조회(인기 랭킹 + 무작위 추천) */
    fun getCuration(userId: Long?): ProductCurationResult
}
