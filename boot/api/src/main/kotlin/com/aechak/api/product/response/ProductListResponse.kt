package com.aechak.api.product.response

import com.aechak.application.product.usecase.result.ProductSummaryResult
import com.aechak.application.support.CursorPageResult

data class ProductListResponse(
    val products: List<ProductSummaryResponse>,
    val totalCount: Long?,
    val nextCursor: String?,
    val hasNext: Boolean,
) {
    companion object {
        fun from(result: CursorPageResult<ProductSummaryResult>): ProductListResponse =
            ProductListResponse(
                products = result.items.map(ProductSummaryResponse::from),
                totalCount = result.totalCount,
                nextCursor = result.nextCursor,
                hasNext = result.hasNext,
            )
    }
}
