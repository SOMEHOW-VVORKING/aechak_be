package com.aechak.application.order.admin.usecase.query

import com.aechak.domain.order.order.enums.OrderStatus
import java.time.LocalDate

/**
 * 어드민 주문 목록 조회 입력.
 *
 * - [status]가 null이면 결제대기를 뺀 전체 상태
 * - [delayedOnly]면 지연 신호가 붙은 주문만
 *
 * 지연 기한의 기준 시각은 싣지 않는다 — 파사드가 요청마다 한 번 떠서 목록 행 판정과 "지연만" 필터에
 * 같은 값을 넘긴다. 입력에 두면 호출자가 임의 시각으로 기준을 밀 수 있다.
 */
data class AdminOrderSearchQuery(
    val status: OrderStatus?,
    val sellerId: Long?,
    val keyword: String?,
    val orderedFrom: LocalDate?,
    val orderedTo: LocalDate?,
    val delayedOnly: Boolean,
    val cursor: String?,
    val size: Int,
)
