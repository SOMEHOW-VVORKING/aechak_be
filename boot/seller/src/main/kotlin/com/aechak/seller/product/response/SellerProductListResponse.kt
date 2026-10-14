package com.aechak.seller.product.response

import com.aechak.application.product.product.usecase.result.SellerProductSummaryResult
import com.aechak.application.support.OffsetPageResult

data class SellerProductListResponse(
    val products: List<SellerProductSummaryResponse>,
    val totalCount: Long,
    val page: Int,
    val size: Int,
    val totalPages: Int,
    val hasNext: Boolean,
) {
    companion object {
        fun from(result: OffsetPageResult<SellerProductSummaryResult>): SellerProductListResponse =
            SellerProductListResponse(
                products = result.items.map(SellerProductSummaryResponse::from),
                totalCount = result.totalCount,
                page = result.page,
                size = result.size,
                totalPages = result.totalPages,
                hasNext = result.hasNext,
            )
    }
}
