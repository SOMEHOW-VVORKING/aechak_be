package com.aechak.admin.claim.response;

import com.aechak.application.order.claim.admin.usecase.result.AdminClaimSummaryResult;
import com.aechak.domain.order.claim.enums.ClaimFaultDecision;
import com.aechak.domain.order.claim.enums.ClaimReasonCode;
import com.aechak.domain.order.claim.enums.ClaimSettlementStatus;
import com.aechak.domain.order.claim.enums.ClaimStatus;
import com.aechak.domain.order.claim.enums.ClaimType;
import com.aechak.domain.order.claim.enums.RefundStatus;
import com.aechak.domain.order.claim.enums.ReturnCostBearer;
import com.aechak.domain.order.claim.enums.SellerApprovalStatus;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 클레임 목록 한 행. 상태는 네 축(클레임 상태·셀러 승인·환불·정산)을 열거 그대로 내리고 한 값으로 접지 않는다.
 *
 * <p>disputeElapsedDays는 기본 정렬(오래된 순)이 왜 그 순서인지를 화면에서 읽을 수 있게 하는 값이고,
 * interventionAllowed는 지금 개입 가능한 건인지다.
 */
public record AdminClaimSummaryResponse(
        String claimPublicId,
        String orderPublicId,
        ClaimType claimType,
        ClaimReasonCode reasonCode,
        String reasonLabel,
        String reasonDetail,
        ClaimStatus status,
        RefundStatus refundStatus,
        SellerApprovalStatus sellerApprovalStatus,
        ClaimSettlementStatus settlementStatus,
        ReturnCostBearer returnCostBearer,
        long refundAmount,
        Long returnShippingFee,
        List<String> productNames,
        int itemCount,
        long sellerId,
        String sellerName,
        String buyerPublicId,
        String buyerNickname,
        ClaimFaultDecision faultDecision,
        boolean interventionAllowed,
        LocalDateTime requestedAt,
        LocalDateTime autoApproveDueAt,
        LocalDateTime approvedAt,
        LocalDateTime resolvedAt,
        Long disputeElapsedDays) {

    public static AdminClaimSummaryResponse from(AdminClaimSummaryResult result) {
        return new AdminClaimSummaryResponse(
                result.getClaimPublicId(),
                result.getOrderPublicId(),
                result.getClaimType(),
                result.getReasonCode(),
                // 화면 문구는 서버 enum이 정본이다 — 어드민 FE가 코드-문구 표를 따로 들지 않게 함께 내린다
                result.getReasonCode() == null ? null : result.getReasonCode().getLabel(),
                result.getReasonDetail(),
                result.getStatus(),
                result.getRefundStatus(),
                result.getSellerApprovalStatus(),
                result.getSettlementStatus(),
                result.getReturnCostBearer(),
                result.getRefundAmount(),
                result.getReturnShippingFee(),
                result.getProductNames(),
                result.getItemCount(),
                result.getSellerId(),
                result.getSellerName(),
                result.getBuyerPublicId(),
                result.getBuyerNickname(),
                result.getFaultDecision(),
                result.getInterventionAllowed(),
                result.getRequestedAt(),
                result.getAutoApproveDueAt(),
                result.getApprovedAt(),
                result.getResolvedAt(),
                result.getDisputeElapsedDays());
    }
}
