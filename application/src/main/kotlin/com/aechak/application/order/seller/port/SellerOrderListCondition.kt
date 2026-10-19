package com.aechak.application.order.seller.port

import com.aechak.domain.order.order.enums.OrderStatus
import java.time.LocalDateTime

/**
 * 셀러 주문 목록 조회 조건
 *
 * - sellerId로 잠가 자기 주문만 조회한다
 * - statuses는 세그먼트가 환산한 상태 집합 — 결제대기는 어느 세그먼트에도 들어오지 않는다
 * - orderedToExclusive: 주문일 상한(미포함) — 날짜 필터를 [from, to+1일) 반개구간으로 환산한 값
 */
data class SellerOrderListCondition(
    val sellerId: Long,
    val statuses: Set<OrderStatus>,
    val keyword: String?,
    val orderedFrom: LocalDateTime?,
    val orderedToExclusive: LocalDateTime?,
    val offset: Long,
    val limit: Int,
) {
    init {
        require(statuses.isNotEmpty()) { "상태 집합은 비어 있을 수 없습니다." }
        require(offset >= 0) { "offset은 0 이상이어야 합니다." }
        require(limit > 0) { "limit은 양수여야 합니다." }
    }
}
