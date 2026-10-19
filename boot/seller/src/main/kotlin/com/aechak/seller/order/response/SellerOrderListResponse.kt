package com.aechak.seller.order.response

import com.aechak.application.order.seller.usecase.result.SellerOrderSummaryResult
import com.aechak.application.support.OffsetPageResult

data class SellerOrderListResponse(
    val orders: List<SellerOrderSummaryResponse>,
    val totalCount: Long,
    val page: Int,
    val size: Int,
    val totalPages: Int,
    val hasNext: Boolean,
) {
    companion object {
        fun from(result: OffsetPageResult<SellerOrderSummaryResult>): SellerOrderListResponse =
            SellerOrderListResponse(
                orders = result.items.map(SellerOrderSummaryResponse::from),
                totalCount = result.totalCount,
                page = result.page,
                size = result.size,
                totalPages = result.totalPages,
                hasNext = result.hasNext,
            )
    }
}
