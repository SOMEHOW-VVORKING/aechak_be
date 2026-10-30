package com.aechak.application.order.admin.port

import com.aechak.application.order.admin.support.OrderDelayDeadlines
import com.aechak.domain.order.order.enums.OrderStatus
import java.time.LocalDateTime

/**
 * 어드민 주문 목록 조회 조건. 셀러 스코프가 없다 — 운영자는 전체를 본다.
 *
 * - statuses: 결제대기는 호출자가 미리 뺀다. 결제 전 주문도 테이블에 남는 모델이라 넣으면 장바구니 단계가 보인다
 * - orderedToExclusive: 주문일 상한(미포함) — 날짜 필터를 [from, to+1일) 반개구간으로 환산한 값
 * - deadlines: 지연 판정 경계. 목록 행 판정과 같은 값을 써야 "지연만" 필터와 행의 신호가 일치한다
 */
data class AdminOrderMonitorCondition(
    val statuses: Set<OrderStatus>,
    val sellerId: Long?,
    val keyword: String?,
    val orderedFrom: LocalDateTime?,
    val orderedToExclusive: LocalDateTime?,
    val delayedOnly: Boolean,
    val deadlines: OrderDelayDeadlines,
    val cursor: AdminOrderCursor?,
    val limit: Int,
) {
    init {
        require(statuses.isNotEmpty()) { "상태 집합은 비어 있을 수 없습니다." }
        require(limit > 0) { "limit은 양수여야 합니다." }
    }
}
