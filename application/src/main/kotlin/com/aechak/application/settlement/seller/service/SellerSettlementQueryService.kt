package com.aechak.application.settlement.seller.service

import com.aechak.application.settlement.seller.port.SellerSettlementSummaryQueryPort
import com.aechak.application.settlement.seller.usecase.result.SellerSettlementSummaryResult
import org.springframework.stereotype.Service

/** 셀러 정산 조회 로직 보관함 — Facade에서만 호출된다. */
@Service
class SellerSettlementQueryService(
    private val sellerSettlementSummaryQueryPort: SellerSettlementSummaryQueryPort,
) {
    fun getSummary(sellerId: Long): SellerSettlementSummaryResult =
        SellerSettlementSummaryResult.from(sellerSettlementSummaryQueryPort.findSummary(sellerId))
}
