package com.aechak.seller.settlement.response

import com.aechak.application.settlement.seller.usecase.result.SellerSettlementSummaryResult
import com.aechak.application.support.OffsetPageResult

data class SellerSettlementListResponse(
    val settlements: List<SellerSettlementSummaryResponse>,
    val totalCount: Long,
    val page: Int,
    val size: Int,
    val totalPages: Int,
    val hasNext: Boolean,
) {
    companion object {
        fun from(result: OffsetPageResult<SellerSettlementSummaryResult>): SellerSettlementListResponse =
            SellerSettlementListResponse(
                settlements = result.items.map(SellerSettlementSummaryResponse::from),
                totalCount = result.totalCount,
                page = result.page,
                size = result.size,
                totalPages = result.totalPages,
                hasNext = result.hasNext,
            )
    }
}
