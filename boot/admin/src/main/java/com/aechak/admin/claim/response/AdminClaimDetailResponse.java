package com.aechak.admin.claim.response;

import com.aechak.application.order.claim.admin.usecase.result.AdminClaimDetailResult;
import com.aechak.application.order.claim.usecase.result.SellerClaimHistoryEvent;
import com.aechak.domain.order.claim.enums.ClaimReasonCode;
import com.aechak.domain.order.claim.enums.ReturnCostBearer;
import com.aechak.domain.order.claim.enums.SellerApprovalStatus;
import com.aechak.domain.order.order.enums.OrderItemStatus;
import com.aechak.domain.order.order.enums.OrderStatus;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 클레임 상세. buyerStatement(구매자 주장)와 sellerHandling(셀러 처리)을 따로 떼어 내린다 —
 * 어느 한쪽만 보면 귀책을 판정할 수 없어 둘이 항상 함께 실려야 한다.
 *
 * <p>intervention은 아직 개입하지 않은 건에서 null이다.
 */
public record AdminClaimDetailResponse(
        AdminClaimSummaryResponse claim,
        OrderResponse order,
        BuyerStatementResponse buyerStatement,
        SellerHandlingResponse sellerHandling,
        List<ItemResponse> items,
        List<HistoryResponse> history,
        AdminClaimInterventionResponse intervention) {

    /** 수령인명·연락처는 복호 평문이다 — 마스킹은 화면이 한다. */
    public record OrderResponse(
            String orderPublicId,
            OrderStatus orderStatus,
            LocalDateTime orderedAt,
            long sellerShippingFee,
            String recipientName,
            String contactNumber,
            String zipCode,
            String baseAddress,
            String detailAddress,
            String deliveryMemo) {}

    public record BuyerStatementResponse(
            ClaimReasonCode reasonCode, String reasonLabel, String reasonDetail, LocalDateTime requestedAt) {}

    /** sellerMemo는 셀러 운영 메모다 — 구매자에게는 나가지 않지만 판정 근거라 운영자에게는 보인다. */
    public record SellerHandlingResponse(
            SellerApprovalStatus sellerApprovalStatus,
            String rejectionReason,
            String sellerMemo,
            ReturnCostBearer returnCostBearer,
            LocalDateTime autoApproveDueAt,
            LocalDateTime approvedAt,
            LocalDateTime collectionStartedAt) {}

    public record ItemResponse(
            long orderItemId,
            String productName,
            String optionName,
            int quantity,
            long unitPrice,
            long refundAmount,
            OrderItemStatus itemStatus) {}

    public record HistoryResponse(SellerClaimHistoryEvent event, LocalDateTime occurredAt) {}

    public static AdminClaimDetailResponse from(AdminClaimDetailResult result) {
        var order = result.getOrder();
        var buyer = result.getBuyerStatement();
        var seller = result.getSellerHandling();
        return new AdminClaimDetailResponse(
                AdminClaimSummaryResponse.from(result.getClaim()),
                new OrderResponse(
                        order.getOrderPublicId(),
                        order.getOrderStatus(),
                        order.getOrderedAt(),
                        order.getSellerShippingFee(),
                        order.getRecipientName(),
                        order.getContactNumber(),
                        order.getZipCode(),
                        order.getBaseAddress(),
                        order.getDetailAddress(),
                        order.getDeliveryMemo()),
                new BuyerStatementResponse(
                        buyer.getReasonCode(), buyer.getReasonLabel(), buyer.getReasonDetail(), buyer.getRequestedAt()),
                new SellerHandlingResponse(
                        seller.getSellerApprovalStatus(),
                        seller.getRejectionReason(),
                        seller.getSellerMemo(),
                        seller.getReturnCostBearer(),
                        seller.getAutoApproveDueAt(),
                        seller.getApprovedAt(),
                        seller.getCollectionStartedAt()),
                result.getItems().stream()
                        .map(item -> new ItemResponse(
                                item.getOrderItemId(),
                                item.getProductName(),
                                item.getOptionName(),
                                item.getQuantity(),
                                item.getUnitPrice(),
                                item.getRefundAmount(),
                                item.getItemStatus()))
                        .toList(),
                result.getHistory().stream()
                        .map(entry -> new HistoryResponse(entry.getEvent(), entry.getOccurredAt()))
                        .toList(),
                AdminClaimInterventionResponse.from(result.getIntervention()));
    }
}
