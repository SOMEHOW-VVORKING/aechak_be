package com.aechak.application.settlement.seller.facade

import com.aechak.application.seller.usecase.SellerUseCase
import com.aechak.application.settlement.seller.service.SellerSettlementQueryService
import com.aechak.application.settlement.seller.usecase.SellerSettlementUseCase
import com.aechak.application.settlement.seller.usecase.query.SellerSettlementSearchQuery
import com.aechak.application.settlement.seller.usecase.result.SellerSettlementDetailResult
import com.aechak.application.settlement.seller.usecase.result.SellerSettlementListItemResult
import com.aechak.application.settlement.seller.usecase.result.SellerSettlementMonthsResult
import com.aechak.application.settlement.seller.usecase.result.SellerSettlementSummaryResult
import com.aechak.application.support.OffsetPageResult
import com.aechak.common.error.BusinessException
import com.aechak.domain.seller.seller.enums.SellerStatus
import com.aechak.domain.settlement.error.SettlementErrorCode
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * SellerSettlementUseCase의 유일한 구현체. 트랜잭션 경계는 여기 고정.
 * 셀러센터 전용이라 셀러 자격(application.seller)을 요구한다 — 배치는 이 패키지를 스캔에서 제외한다.
 */
@Service
class SellerSettlementFacade(
    private val sellerSettlementQueryService: SellerSettlementQueryService,
    private val sellerUseCase: SellerUseCase,
) : SellerSettlementUseCase {
    @Transactional(readOnly = true)
    override fun searchSettlements(query: SellerSettlementSearchQuery): OffsetPageResult<SellerSettlementListItemResult> {
        requireSettlementReadableSeller(query.sellerId)
        return sellerSettlementQueryService.search(query)
    }

    @Transactional(readOnly = true)
    override fun getSettlement(
        orderPublicId: String,
        sellerId: Long,
    ): SellerSettlementDetailResult {
        requireSettlementReadableSeller(sellerId)
        return sellerSettlementQueryService.getSettlement(orderPublicId, sellerId)
    }

    @Transactional(readOnly = true)
    override fun getSettlementMonths(sellerId: Long): SellerSettlementMonthsResult {
        requireSettlementReadableSeller(sellerId)
        return sellerSettlementQueryService.getSettlementMonths(sellerId)
    }

    /** 자격 게이트를 다시 두지 않는다 — 대시보드가 자기 게이트를 지난 뒤 부르는 경로다. */
    @Transactional(readOnly = true)
    override fun getSummary(sellerId: Long): SellerSettlementSummaryResult = sellerSettlementQueryService.getSummary(sellerId)

    /**
     * 조회 자격 게이트 — 이미 발생한 정산은 셀러가 받을 돈의 기록이라 휴점·탈퇴신청 중에도 봐야 한다.
     * 자격 소멸(탈퇴)과 제재(정지)만 막는다.
     */
    private fun requireSettlementReadableSeller(sellerId: Long) {
        if (sellerUseCase.requireSellerStatus(sellerId) !in SETTLEMENT_READABLE_SELLER_STATUSES) {
            throw BusinessException(SettlementErrorCode.SETTLEMENT_ACCESS_DENIED)
        }
    }

    companion object {
        private val SETTLEMENT_READABLE_SELLER_STATUSES =
            setOf(SellerStatus.ACTIVE, SellerStatus.PAUSED, SellerStatus.WITHDRAWAL_REQUESTED)
    }
}
