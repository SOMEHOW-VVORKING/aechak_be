package com.aechak.application.settlement.port

import com.aechak.application.settlement.port.view.SellerSettlementItemView
import com.aechak.application.settlement.port.view.SellerSettlementOrderView

/**
 * 셀러 자신의 정산 조회 포트.
 *
 * 정산 행은 주문품목 단위고 화면은 주문 단위라 집계는 SQL이 끝낸다 — 목록과 상세가 같은 집계식을 쓰므로
 * "요약과 내역의 합"이 어긋날 수 없다. 취소된 정산 행은 모든 메서드에서 집계 대상이 아니다.
 */
interface SellerSettlementQueryPort {
    /** 주문 단위로 접은 목록 한 페이지 — 구매확정일 최신순 */
    fun findOrderPage(condition: SellerSettlementCondition): List<SellerSettlementOrderView>

    /** 같은 조건의 주문 수 */
    fun countOrders(condition: SellerSettlementCondition): Long

    /**
     * 주문 하나의 집계. 셀러 범위를 걸지 않고 소유자를 함께 돌려준다 —
     * 호출자가 "없는 주문"과 "남의 주문"을 가려 응답을 나눈다.
     */
    fun findOrder(orderPublicId: String): SellerSettlementOrderView?

    /** 주문 하나의 정산 행별 내역 — 주문품목 id 순 */
    fun findItems(orderId: Long): List<SellerSettlementItemView>

    /** 정산 행이 있는 월만 최신순으로 — 최대 [limit]개 */
    fun findSettlementMonths(
        sellerId: Long,
        limit: Int,
    ): List<String>
}
