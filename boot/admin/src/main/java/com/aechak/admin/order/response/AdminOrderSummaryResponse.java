package com.aechak.admin.order.response;

import com.aechak.application.order.admin.usecase.result.AdminOrderClaimResult;
import com.aechak.application.order.admin.usecase.result.AdminOrderDelayReason;
import com.aechak.application.order.admin.usecase.result.AdminOrderShipmentResult;
import com.aechak.application.order.admin.usecase.result.AdminOrderSummaryResult;
import com.aechak.domain.order.claim.enums.ClaimStatus;
import com.aechak.domain.order.claim.enums.ClaimType;
import com.aechak.domain.order.order.enums.OrderStatus;
import java.time.LocalDateTime;

/**
 * 주문 한 건의 목록 행. 상세도 같은 모양을 품는다.
 *
 * <p>상태·지연 사유·클레임은 enum 타입 그대로 싣는다 — 문자열로 접으면 생성 문서에 허용값이 실리지 않는다.
 *
 * <p>shipment가 null이면 송장 미등록이다. delayReason이 null이면 정상 흐름이고, 판정 기준은 서버가 쥔다
 * — 화면이 날짜를 빼서 세면 카드의 건수와 행의 신호가 어긋난다.
 *
 * <p>수령인명·연락처·주소는 담지 않는다. 배송 진행 감시에 쓰이지 않는 개인정보다.
 */
public record AdminOrderSummaryResponse(
        String orderId,
        OrderStatus status,
        LocalDateTime orderedAt,
        long sellerId,
        String sellerName,
        String buyerNickname,
        String representativeItemName,
        int itemCount,
        long totalProductAmount,
        long sellerShippingFee,
        long allocatedCouponDiscount,
        long payableAmount,
        LocalDateTime purchaseConfirmDueAt,
        boolean delayed,
        AdminOrderDelayReason delayReason,
        ShipmentResponse shipment,
        ClaimResponse activeClaim) {

    public record ShipmentResponse(
            String courierCode,
            String courierName,
            String trackingNumber,
            LocalDateTime dispatchedAt,
            LocalDateTime deliveredAt,
            String lastTrackingStatus) {

        static ShipmentResponse from(AdminOrderShipmentResult result) {
            return new ShipmentResponse(
                    result.getCourierCode(),
                    result.getCourierName(),
                    result.getTrackingNumber(),
                    result.getDispatchedAt(),
                    result.getDeliveredAt(),
                    result.getLastTrackingStatus());
        }
    }

    public record ClaimResponse(String claimId, ClaimType claimType, ClaimStatus status) {

        static ClaimResponse from(AdminOrderClaimResult result) {
            return new ClaimResponse(result.getClaimId(), result.getClaimType(), result.getStatus());
        }
    }

    public static AdminOrderSummaryResponse from(AdminOrderSummaryResult result) {
        return new AdminOrderSummaryResponse(
                result.getOrderId(),
                result.getStatus(),
                result.getOrderedAt(),
                result.getSellerId(),
                result.getSellerName(),
                result.getBuyerNickname(),
                result.getRepresentativeItemName(),
                result.getItemCount(),
                result.getTotalProductAmount(),
                result.getSellerShippingFee(),
                result.getAllocatedCouponDiscount(),
                result.getPayableAmount(),
                result.getPurchaseConfirmDueAt(),
                result.getDelayed(),
                result.getDelayReason(),
                result.getShipment() == null ? null : ShipmentResponse.from(result.getShipment()),
                result.getActiveClaim() == null ? null : ClaimResponse.from(result.getActiveClaim()));
    }
}
