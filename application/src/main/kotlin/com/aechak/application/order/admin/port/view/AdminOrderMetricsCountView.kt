package com.aechak.application.order.admin.port.view

/**
 * 주문 테이블 한 번만 훑어 받는 지표 묶음 — 지표마다 쿼리를 따로 돌면 카드 수만큼 집계가 붙는다.
 * [netFeeAmount]는 셀러 배송비에서 안분 쿠폰 할인을 뺀 합이다. 상품 금액 합은 품목 테이블을
 * 조인해야 하고 종결된 품목을 빼야 해서 따로 센다(한 쿼리로 묶으면 배송비가 품목 수만큼 중복된다).
 */
data class AdminOrderMetricsCountView(
    val awaitingDispatchCount: Long,
    val shippingCount: Long,
    val delayedCount: Long,
    val netFeeAmount: Long,
)
