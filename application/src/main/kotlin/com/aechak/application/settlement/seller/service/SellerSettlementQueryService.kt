package com.aechak.application.settlement.seller.service

import com.aechak.application.settlement.port.SellerSettlementCondition
import com.aechak.application.settlement.port.SellerSettlementQueryPort
import com.aechak.application.settlement.seller.port.SellerSettlementSummaryQueryPort
import com.aechak.application.settlement.seller.usecase.query.SellerSettlementSearchQuery
import com.aechak.application.settlement.seller.usecase.result.SellerSettlementDetailResult
import com.aechak.application.settlement.seller.usecase.result.SellerSettlementListItemResult
import com.aechak.application.settlement.seller.usecase.result.SellerSettlementMonthsResult
import com.aechak.application.settlement.seller.usecase.result.SellerSettlementSummaryResult
import com.aechak.application.support.OffsetPageResult
import com.aechak.common.error.BusinessException
import com.aechak.domain.settlement.error.SettlementErrorCode
import org.springframework.stereotype.Service

/** 셀러 정산 조회 로직 보관함 — Facade에서만 호출된다. */
@Service
class SellerSettlementQueryService(
    private val sellerSettlementQueryPort: SellerSettlementQueryPort,
    private val sellerSettlementSummaryQueryPort: SellerSettlementSummaryQueryPort,
) {
    fun search(query: SellerSettlementSearchQuery): OffsetPageResult<SellerSettlementListItemResult> {
        val condition =
            SellerSettlementCondition(
                sellerId = query.sellerId,
                settlementMonth = query.settlementMonth,
                status = query.status,
                offset = query.page.toLong() * query.size,
                limit = query.size,
            )
        return OffsetPageResult(
            items = sellerSettlementQueryPort.findOrderPage(condition).map(SellerSettlementListItemResult::from),
            totalCount = sellerSettlementQueryPort.countOrders(condition),
            page = query.page,
            size = query.size,
        )
    }

    /** 정산 행이 없는 주문과 남의 주문을 가른다 — 공개 식별자를 아는 것이 열람 권한이 되지 않게 소유자를 직접 대조한다 */
    fun getSettlement(
        orderPublicId: String,
        sellerId: Long,
    ): SellerSettlementDetailResult {
        val order =
            sellerSettlementQueryPort.findOrder(orderPublicId)
                ?: throw BusinessException(SettlementErrorCode.SETTLEMENT_NOT_FOUND)
        if (order.sellerId != sellerId) {
            throw BusinessException(SettlementErrorCode.SETTLEMENT_ACCESS_DENIED)
        }
        return SellerSettlementDetailResult.from(order, sellerSettlementQueryPort.findItems(order.orderId))
    }

    fun getSettlementMonths(sellerId: Long): SellerSettlementMonthsResult =
        SellerSettlementMonthsResult(sellerSettlementQueryPort.findSettlementMonths(sellerId, MONTHS_LIMIT))

    fun getSummary(sellerId: Long): SellerSettlementSummaryResult =
        SellerSettlementSummaryResult.from(sellerSettlementSummaryQueryPort.findSummary(sellerId))

    companion object {
        /** 월 필터가 보여 줄 범위 — 그 앞의 정산은 월을 직접 지정해도 목록 조회로 볼 수 있다 */
        private const val MONTHS_LIMIT = 24
    }
}
