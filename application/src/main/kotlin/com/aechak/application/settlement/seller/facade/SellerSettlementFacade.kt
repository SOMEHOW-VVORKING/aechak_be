package com.aechak.application.settlement.seller.facade

import com.aechak.application.settlement.seller.service.SellerSettlementQueryService
import com.aechak.application.settlement.seller.usecase.SellerSettlementUseCase
import com.aechak.application.settlement.seller.usecase.result.SellerSettlementSummaryResult
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** SellerSettlementUseCase의 유일한 구현체. @Transactional 경계는 여기 고정. */
@Service
class SellerSettlementFacade(
    private val sellerSettlementQueryService: SellerSettlementQueryService,
) : SellerSettlementUseCase {
    @Transactional(readOnly = true)
    override fun getSummary(sellerId: Long): SellerSettlementSummaryResult = sellerSettlementQueryService.getSummary(sellerId)
}
