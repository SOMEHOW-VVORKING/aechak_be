package com.aechak.application.settlement.seller.port.view

/** 미지급 정산 금액 — 상태별 지급 예정액 합. 지급 완료·취소 행은 포함하지 않는다. */
data class SellerSettlementSummaryView(
    val pendingAmount: Long,
    val onHoldAmount: Long,
)
