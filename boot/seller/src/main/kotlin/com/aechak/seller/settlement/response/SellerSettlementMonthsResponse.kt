package com.aechak.seller.settlement.response

import com.aechak.application.settlement.seller.usecase.result.SellerSettlementMonthsResult

data class SellerSettlementMonthsResponse(
    val months: List<String>,
) {
    companion object {
        fun from(result: SellerSettlementMonthsResult): SellerSettlementMonthsResponse =
            SellerSettlementMonthsResponse(months = result.months)
    }
}
