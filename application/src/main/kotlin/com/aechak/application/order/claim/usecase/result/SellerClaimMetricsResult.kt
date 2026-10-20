package com.aechak.application.order.claim.usecase.result

import com.aechak.application.order.claim.port.view.SellerClaimTypeCountView
import com.aechak.domain.order.claim.enums.ClaimType

/** 셀러가 응답해야 할 클레임 건수 — 유형별. */
data class SellerClaimMetricsResult(
    val cancelRequests: Long,
    val returnRequests: Long,
    val exchangeRequests: Long,
) {
    companion object {
        fun from(counts: List<SellerClaimTypeCountView>): SellerClaimMetricsResult {
            val byType = counts.associate { it.claimType to it.count }
            return SellerClaimMetricsResult(
                cancelRequests = byType[ClaimType.CANCEL] ?: 0L,
                returnRequests = byType[ClaimType.RETURN] ?: 0L,
                exchangeRequests = byType[ClaimType.EXCHANGE] ?: 0L,
            )
        }
    }
}
