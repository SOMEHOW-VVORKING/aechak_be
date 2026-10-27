package com.aechak.application.settlement.seller.usecase

import com.aechak.application.settlement.seller.usecase.query.SellerSettlementSearchQuery
import com.aechak.application.settlement.seller.usecase.result.SellerSettlementDetailResult
import com.aechak.application.settlement.seller.usecase.result.SellerSettlementListItemResult
import com.aechak.application.settlement.seller.usecase.result.SellerSettlementMonthsResult
import com.aechak.application.settlement.seller.usecase.result.SellerSettlementSummaryResult
import com.aechak.application.support.OffsetPageResult

/**
 * 셀러센터가 쓰는 정산 조회 계약. 원장 생성·계좌 등록을 담은 SettlementUseCase와 나눠 둔다 —
 * 소비하는 실행 모듈이 갈리고(셀러센터 vs api·배치·어드민) 주입 의존도 겹치지 않는다.
 *
 * 셀러 자격 게이트가 메서드마다 다르다. 정산 화면이 직접 부르는 세 조회는 이 계약의 구현이 게이트를
 * 지나고, 요약은 대시보드가 자기 게이트를 지난 뒤 부른다. 호출자가 갈려 그렇게 됐다.
 */
interface SellerSettlementUseCase {
    /** 내 정산 목록 — 월·상태 옵셔널 필터 + 오프셋 페이지네이션. 주문 하나가 한 행이다 */
    fun searchSettlements(query: SellerSettlementSearchQuery): OffsetPageResult<SellerSettlementListItemResult>

    /** 내 정산 한 건 — 주문 공개 식별자로 찾는다. 없으면 404, 남의 정산이면 403 */
    fun getSettlement(
        orderPublicId: String,
        sellerId: Long,
    ): SellerSettlementDetailResult

    /** 월 필터가 고를 수 있는 정산월 목록 */
    fun getSettlementMonths(sellerId: Long): SellerSettlementMonthsResult

    /**
     * 미지급 정산 요약 — 기간 필터가 없는 현재 스냅샷이다. 지급 예정일이 지났더라도
     * 집행 전이면 대기로 남는다.
     *
     * 전제 — 셀러 자격 게이트는 호출자가 통과시킨다. 집계는 넘겨받은 sellerId 범위만 읽는다.
     */
    fun getSummary(sellerId: Long): SellerSettlementSummaryResult
}
