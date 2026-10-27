package com.aechak.application.settlement.seller.usecase.result

/** 선택 가능한 정산월 — 최신순. 정산 행이 있는 월만 담긴다 */
data class SellerSettlementMonthsResult(
    val months: List<String>,
)
