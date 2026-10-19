package com.aechak.application.order.delivery.usecase.query

import java.time.LocalDateTime

/**
 * 미배송 발송 스캔 조건. 발송 식별자 커서로 회차 안에서 페이지를 넘긴다 —
 * 상한에서 끊고 끝내면 실패가 쌓인 앞자리가 뒷건의 처리를 영구히 막는다.
 */
data class PendingDeliveryQuery(
    val dispatchedBefore: LocalDateTime,
    val afterShipmentId: Long,
    val limit: Int,
)
