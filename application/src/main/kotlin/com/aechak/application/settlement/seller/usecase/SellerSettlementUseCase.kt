package com.aechak.application.settlement.seller.usecase

import com.aechak.application.settlement.seller.usecase.query.SellerSettlementSearchQuery
import com.aechak.application.settlement.seller.usecase.result.SellerSettlementDetailResult
import com.aechak.application.settlement.seller.usecase.result.SellerSettlementMonthsResult
import com.aechak.application.settlement.seller.usecase.result.SellerSettlementSummaryResult
import com.aechak.application.support.OffsetPageResult

/**
 * 셀러센터가 쓰는 정산 조회 계약. 구매확정·심사 승인이 쓰는 SettlementUseCase와 나눠 둔다 —
 * 이쪽은 셀러 자격 게이트를 지나고 저쪽은 타 BC의 쓰기 트랜잭션에 참여한다.
 */
interface SellerSettlementUseCase {
    /** 내 정산 목록 — 월·상태 옵셔널 필터 + 오프셋 페이지네이션. 주문 하나가 한 행이다 */
    fun searchSettlements(query: SellerSettlementSearchQuery): OffsetPageResult<SellerSettlementSummaryResult>

    /** 내 정산 한 건 — 주문 공개 식별자로 찾는다. 없으면 404, 남의 정산이면 403 */
    fun getSettlement(
        orderPublicId: String,
        sellerId: Long,
    ): SellerSettlementDetailResult

    /** 월 필터가 고를 수 있는 정산월 목록 */
    fun getSettlementMonths(sellerId: Long): SellerSettlementMonthsResult
}
