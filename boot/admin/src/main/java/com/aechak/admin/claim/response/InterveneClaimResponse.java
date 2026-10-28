package com.aechak.admin.claim.response;

import com.aechak.application.order.claim.admin.usecase.result.InterveneClaimResult;
import com.aechak.domain.order.claim.enums.ClaimStatus;
import com.aechak.domain.order.claim.enums.RefundStatus;
import com.aechak.domain.order.claim.enums.ReturnCostBearer;

/**
 * 개입 결과.
 *
 * <p>refundStatus/refundSettled가 함께 실리는 이유: 개입은 반품비 부담 주체만 정하고 환불 집행·결제 취소는
 * 건드리지 않는다. 판정 버튼을 누른 운영자가 "환불까지 처리됐다"고 읽으면 돌려주지 않은 돈을 처리된 것으로
 * 보게 되므로 환불 축을 그대로 내려 그렇지 않음을 못박는다.
 */
public record InterveneClaimResponse(
        String claimPublicId,
        ClaimStatus status,
        ReturnCostBearer returnCostBearer,
        RefundStatus refundStatus,
        boolean refundSettled,
        AdminClaimInterventionResponse intervention) {

    public static InterveneClaimResponse from(InterveneClaimResult result) {
        return new InterveneClaimResponse(
                result.getClaimPublicId(),
                result.getStatus(),
                result.getReturnCostBearer(),
                result.getRefundStatus(),
                result.getRefundSettled(),
                AdminClaimInterventionResponse.from(result.getIntervention()));
    }
}
