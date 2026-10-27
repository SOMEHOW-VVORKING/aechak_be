package com.aechak.admin.claim.response;

import com.aechak.application.order.claim.admin.usecase.result.AdminClaimInterventionResult;
import com.aechak.domain.order.claim.enums.ClaimFaultDecision;
import com.aechak.domain.order.claim.enums.ClaimInterventionReason;
import com.aechak.domain.order.claim.enums.ClaimStatus;
import com.aechak.domain.order.claim.enums.ReturnCostBearer;
import com.aechak.domain.order.claim.enums.SellerApprovalStatus;
import java.time.LocalDateTime;

/**
 * 개입 기록. previousStatus/previousSellerApprovalStatus가 "무엇을 덮었는지"이고,
 * previousResolvedAt은 뒤집기 전 종결 시각이다(클레임의 종결 시각 칼럼은 개입 시각으로 덮인다).
 */
public record AdminClaimInterventionResponse(
        long adminId,
        ClaimFaultDecision faultDecision,
        boolean claimAccepted,
        ClaimInterventionReason reason,
        String reasonLabel,
        String reasonDetail,
        ReturnCostBearer returnCostBearer,
        ClaimStatus previousStatus,
        SellerApprovalStatus previousSellerApprovalStatus,
        LocalDateTime previousResolvedAt,
        String internalMemo,
        LocalDateTime intervenedAt) {

    public static AdminClaimInterventionResponse from(AdminClaimInterventionResult result) {
        if (result == null) {
            return null;
        }
        return new AdminClaimInterventionResponse(
                result.getAdminId(),
                result.getFaultDecision(),
                result.getClaimAccepted(),
                result.getReason(),
                result.getReasonLabel(),
                result.getReasonDetail(),
                result.getReturnCostBearer(),
                result.getPreviousStatus(),
                result.getPreviousSellerApprovalStatus(),
                result.getPreviousResolvedAt(),
                result.getInternalMemo(),
                result.getIntervenedAt());
    }
}
