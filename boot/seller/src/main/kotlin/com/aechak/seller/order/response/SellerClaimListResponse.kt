package com.aechak.seller.order.response

import com.aechak.application.order.claim.usecase.result.SellerClaimPageResult
import com.aechak.domain.order.claim.enums.ClaimType

data class SellerClaimListResponse(
    val claims: List<SellerClaimSummaryResponse>,
    val totalCount: Long,
    val counts: Map<ClaimType, Long>,
    val page: Int,
    val size: Int,
    val totalPages: Int,
    val hasNext: Boolean,
) {
    companion object {
        fun from(result: SellerClaimPageResult): SellerClaimListResponse =
            SellerClaimListResponse(
                claims = result.page.items.map(SellerClaimSummaryResponse::from),
                totalCount = result.page.totalCount,
                counts = result.counts,
                page = result.page.page,
                size = result.page.size,
                totalPages = result.page.totalPages,
                hasNext = result.page.hasNext,
            )
    }
}
