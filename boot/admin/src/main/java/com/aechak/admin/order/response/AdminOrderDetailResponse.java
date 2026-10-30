package com.aechak.admin.order.response;

import com.aechak.application.order.admin.usecase.result.AdminOrderDetailResult;
import com.aechak.application.order.admin.usecase.result.AdminOrderLineResult;
import com.aechak.application.order.admin.usecase.result.AdminOrderStatusHistoryResult;
import com.aechak.domain.order.order.enums.ConfirmType;
import com.aechak.domain.order.order.enums.OrderItemStatus;
import com.aechak.domain.order.order.enums.OrderStatus;
import com.aechak.domain.order.order.enums.TriggeredBy;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 주문 상세. 목록 행과 같은 주문 단위 응답에 품목 전문·상태 전이 이력·독촉 이력을 덧붙인다.
 *
 * <p>배송 진행 타임라인의 재료는 statusHistory다 — 단계 이름과 표시 규칙은 화면이 정한다.
 * 전이 행은 전이가 일어난 트랜잭션에서 동기 insert되므로 occurredAt이 실제 전이 시각이다.
 */
public record AdminOrderDetailResponse(
        AdminOrderSummaryResponse order,
        String orderGroupId,
        LocalDateTime purchaseConfirmedAt,
        ConfirmType confirmType,
        List<ItemResponse> items,
        List<StatusHistoryResponse> statusHistory,
        List<AdminOrderDispatchNudgeResponse> nudges) {

    public record ItemResponse(
            long orderItemId,
            String productId,
            String productName,
            String optionName,
            int quantity,
            long unitPrice,
            long discountAllocatedAmount,
            OrderItemStatus itemStatus) {

        static ItemResponse from(AdminOrderLineResult result) {
            return new ItemResponse(
                    result.getOrderItemId(),
                    result.getProductId(),
                    result.getProductName(),
                    result.getOptionName(),
                    result.getQuantity(),
                    result.getUnitPrice(),
                    result.getDiscountAllocatedAmount(),
                    result.getItemStatus());
        }
    }

    public record StatusHistoryResponse(
            OrderStatus fromStatus, OrderStatus toStatus, TriggeredBy triggeredBy, LocalDateTime occurredAt) {

        static StatusHistoryResponse from(AdminOrderStatusHistoryResult result) {
            return new StatusHistoryResponse(
                    result.getFromStatus(), result.getToStatus(), result.getTriggeredBy(), result.getOccurredAt());
        }
    }

    public static AdminOrderDetailResponse from(AdminOrderDetailResult result) {
        return new AdminOrderDetailResponse(
                AdminOrderSummaryResponse.from(result.getOrder()),
                result.getOrderGroupId(),
                result.getPurchaseConfirmedAt(),
                result.getConfirmType(),
                result.getItems().stream().map(ItemResponse::from).toList(),
                result.getStatusHistory().stream()
                        .map(StatusHistoryResponse::from)
                        .toList(),
                result.getNudges().stream()
                        .map(AdminOrderDispatchNudgeResponse::from)
                        .toList());
    }
}
