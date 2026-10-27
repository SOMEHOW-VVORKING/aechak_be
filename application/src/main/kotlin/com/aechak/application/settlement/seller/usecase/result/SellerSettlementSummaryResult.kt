package com.aechak.application.settlement.seller.usecase.result

import com.aechak.application.settlement.seller.port.view.SellerSettlementSummaryView

/** [totalAmount]는 아직 받지 못한 정산액 전체 — 대기와 보류의 합이다. */
data class SellerSettlementSummaryResult(
    val totalAmount: Long,
    val pendingAmount: Long,
    val onHoldAmount: Long,
) {
    companion object {
        fun from(view: SellerSettlementSummaryView): SellerSettlementSummaryResult =
            SellerSettlementSummaryResult(
                totalAmount = view.pendingAmount + view.onHoldAmount,
                pendingAmount = view.pendingAmount,
                onHoldAmount = view.onHoldAmount,
            )
    }
}
