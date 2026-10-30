package com.aechak.application.settlement.seller.port

import com.aechak.application.settlement.seller.port.view.SellerSettlementSummaryView

/** 셀러 대시보드의 정산 요약 집계 포트. 상태별 합을 쿼리 한 번으로 받는다. */
interface SellerSettlementSummaryQueryPort {
    fun findSummary(sellerId: Long): SellerSettlementSummaryView
}
