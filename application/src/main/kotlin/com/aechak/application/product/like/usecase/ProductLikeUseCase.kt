package com.aechak.application.product.like.usecase

import com.aechak.application.product.like.usecase.command.ProductLikeCommand
import com.aechak.application.product.like.usecase.query.LikedProductListQuery
import com.aechak.application.product.usecase.result.ProductSummaryResult
import com.aechak.application.support.CursorPageResult

interface ProductLikeUseCase {
    /** 상품 찜 추가 */
    fun like(command: ProductLikeCommand)

    /** 상품 찜 취소 */
    fun unlike(command: ProductLikeCommand)

    /** 내 찜 목록 조회 */
    fun getLikedProducts(
        query: LikedProductListQuery,
        userId: Long,
    ): CursorPageResult<ProductSummaryResult>
}
