package com.aechak.application.order.admin.usecase.result

import com.aechak.application.order.admin.port.view.AdminOrderMetricsCountView

/**
 * 어드민 주문 모니터링 카드 넷.
 *
 * - [awaitingDispatchCount]: 결제완료·상품준비·발송대기 — 아직 송장이 붙지 않은 전부다.
 *   셀러가 발주 확인조차 하지 않은 건(결제완료)이 가장 위험한 쪽이라 빼면 카드가 거짓이 된다
 * - [delayedCount]: 발송 지연 + 배송 정체. 목록 행의 지연 신호와 같은 기준으로 센다
 * - [totalOrderAmount]: 조회 범위(셀러·기간)의 **주문 금액** 합 — 상품금액 + 셀러배송비 - 안분
 *   쿠폰할인이다. **실제 수납액이 아니다.** 적립금은 주문그룹 단위로 쓰여 셀러별 주문에 안분할
 *   근거가 없어 여기서 빼지 않으므로, 적립금을 쓴 주문에서 PG 청구액보다 크다. 정산·대사 숫자와
 *   맞춰 볼 값이 아니라 "이 범위에 주문이 얼마나 들어왔는지"를 재는 값이다.
 *   취소 주문과 환불·취소로 종결된 품목은 빠진다
 */
data class AdminOrderMetricsResult(
    val awaitingDispatchCount: Long,
    val shippingCount: Long,
    val delayedCount: Long,
    val totalOrderAmount: Long,
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
                totalOrderAmount = productAmount + counts.netFeeAmount,
            )
    }
}
