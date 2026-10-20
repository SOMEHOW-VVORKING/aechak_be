package com.aechak.application.settlement.seller.usecase

import com.aechak.application.settlement.seller.usecase.result.SellerSettlementSummaryResult

/**
 * 셀러센터가 쓰는 정산 조회 계약. 원장 생성·계좌 등록을 담은 SettlementUseCase와 나눠 둔다 —
 * 소비하는 실행 모듈이 갈리고(셀러센터 vs api·배치·어드민) 주입 의존도 겹치지 않는다.
 */
interface SellerSettlementUseCase {
    /**
     * 미지급 정산 요약 — 기간 필터가 없는 현재 스냅샷이다. 지급 예정일이 지났더라도
     * 집행 전이면 대기로 남는다.
     *
     * 전제 — 셀러 자격 게이트는 호출자가 통과시킨다. 집계는 넘겨받은 sellerId 범위만 읽는다.
     */
    fun getSummary(sellerId: Long): SellerSettlementSummaryResult
}
