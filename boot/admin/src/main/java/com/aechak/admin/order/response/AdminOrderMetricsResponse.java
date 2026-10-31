package com.aechak.admin.order.response;

import com.aechak.application.order.admin.usecase.result.AdminOrderMetricsResult;

/**
 * 모니터링 카드 넷.
 *
 * <ul>
 *   <li>awaitingDispatchCount: 결제완료·상품준비·발송대기 — 아직 송장이 붙지 않은 전부다. 셀러가
 *       발주 확인조차 하지 않은 건을 빼면 가장 위험한 쪽이 카드에서 사라진다.
 *   <li>delayedCount: 발송 지연 + 배송 정체. 목록 행의 지연 신호와 같은 기준으로 센다.
 *   <li>totalOrderAmount: 조회 범위(셀러·기간)의 <b>주문 금액</b> 합이다 — 상품금액 + 셀러배송비 - 안분
 *       쿠폰할인. <b>실제 수납액이 아니다.</b> 적립금은 주문그룹 단위로 쓰여 셀러별 주문에 안분할 근거가
 *       없어 빼지 않으므로 적립금을 쓴 주문에서 PG 청구액보다 크다. 상태·지연 필터는 반영하지 않지만
 *       취소 주문과 환불·취소로 종결된 품목은 빠진다 — 결제 창이 만료된 미결제 주문이 취소로 남으므로,
 *       넣으면 돈이 한 번도 움직이지 않은 주문의 주문금액이 쌓인다. 그래서 건수 카드 셋과 모수가 다르다.
 * </ul>
 */
public record AdminOrderMetricsResponse(
        long awaitingDispatchCount, long shippingCount, long delayedCount, long totalOrderAmount) {

    public static AdminOrderMetricsResponse from(AdminOrderMetricsResult result) {
        return new AdminOrderMetricsResponse(
                result.getAwaitingDispatchCount(),
                result.getShippingCount(),
                result.getDelayedCount(),
                result.getTotalOrderAmount());
    }
}
