package com.aechak.application.order.admin.usecase.result

import com.aechak.application.order.admin.port.view.AdminOrderMetricsCountView

/**
 * 어드민 주문 모니터링 카드 넷.
 *
 * - [awaitingDispatchCount]: 결제완료·상품준비·발송대기 — 아직 송장이 붙지 않은 전부다.
 *   셀러가 발주 확인조차 하지 않은 건(결제완료)이 가장 위험한 쪽이라 빼면 카드가 거짓이 된다
 * - [delayedCount]: 발송 지연 + 배송 정체. 목록 행의 지연 신호와 같은 기준으로 센다
 * - [totalPaymentAmount]: 조회 범위(셀러·기간)의 결제액 합 — 상품금액 + 셀러배송비 - 안분 쿠폰할인.
 *   취소 주문은 빠진다 — 결제 창이 만료된 미결제 주문이 취소로 남으므로, 넣으면 돈이 움직이지 않은
 *   주문까지 결제액에 쌓인다. 건수 카드 셋과 모수가 다르다
 */
data class AdminOrderMetricsResult(
    val awaitingDispatchCount: Long,
    val shippingCount: Long,
    val delayedCount: Long,
    val totalPaymentAmount: Long,
) {
    companion object {
        fun of(
            counts: AdminOrderMetricsCountView,
            productAmount: Long,
        ): AdminOrderMetricsResult =
            AdminOrderMetricsResult(
                awaitingDispatchCount = counts.awaitingDispatchCount,
                shippingCount = counts.shippingCount,
                delayedCount = counts.delayedCount,
                totalPaymentAmount = productAmount + counts.netFeeAmount,
            )
    }
}
