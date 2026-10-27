package com.aechak.admin.order.response;

import com.aechak.application.order.admin.usecase.result.AdminOrderMetricsResult;

/**
 * 모니터링 카드 넷.
 *
 * <ul>
 *   <li>awaitingDispatchCount: 결제완료·상품준비·발송대기 — 아직 송장이 붙지 않은 전부다. 셀러가
 *       발주 확인조차 하지 않은 건을 빼면 가장 위험한 쪽이 카드에서 사라진다.
 *   <li>delayedCount: 발송 지연 + 배송 정체. 목록 행의 지연 신호와 같은 기준으로 센다.
 *   <li>totalPaymentAmount: 조회 범위(셀러·기간)의 결제액 합. 상태·지연 필터는 반영하지 않는다.
 * </ul>
 */
public record AdminOrderMetricsResponse(
        long awaitingDispatchCount, long shippingCount, long delayedCount, long totalPaymentAmount) {

    public static AdminOrderMetricsResponse from(AdminOrderMetricsResult result) {
        return new AdminOrderMetricsResponse(
                result.getAwaitingDispatchCount(),
                result.getShippingCount(),
                result.getDelayedCount(),
                result.getTotalPaymentAmount());
    }
}
