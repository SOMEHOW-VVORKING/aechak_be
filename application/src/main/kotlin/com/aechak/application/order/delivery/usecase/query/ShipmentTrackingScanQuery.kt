package com.aechak.application.order.delivery.usecase.query

/**
 * 추적 갱신 대상 스캔 조건. 발송 식별자 커서로 회차 안에서 페이지를 넘긴다 —
 * 상한에서 끊고 끝내면 앞자리에 쌓인 실패가 뒷건의 처리를 영구히 막는다.
 */
data class ShipmentTrackingScanQuery(
    val afterShipmentId: Long,
    val limit: Int,
)
