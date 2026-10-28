package com.aechak.admin.claim.response;

import com.aechak.application.order.claim.admin.usecase.result.ClaimInterventionReasonCatalogResult;
import com.aechak.domain.order.claim.enums.ClaimFaultDecision;
import com.aechak.domain.order.claim.enums.ClaimInterventionReason;
import java.util.List;

/** 귀책 판정별 개입 사유 선택지. 코드·문구·상세 필수 여부를 함께 내려 화면이 선택지를 조립할 수 있게 한다. */
public record ClaimInterventionReasonCatalogResponse(List<FaultDecisionReasonsResponse> faultDecisions) {

    public record FaultDecisionReasonsResponse(
            ClaimFaultDecision faultDecision, boolean claimAccepted, List<ReasonResponse> reasons) {}

    public record ReasonResponse(ClaimInterventionReason code, String label, boolean detailRequired) {}

    public static ClaimInterventionReasonCatalogResponse from(ClaimInterventionReasonCatalogResult result) {
        return new ClaimInterventionReasonCatalogResponse(result.getReasonsByFaultDecision().entrySet().stream()
                .map(entry -> new FaultDecisionReasonsResponse(
                        entry.getKey(),
                        entry.getKey().getClaimAccepted(),
                        entry.getValue().stream()
                                .map(reason -> new ReasonResponse(reason, reason.getLabel(), reason.requiresDetail()))
                                .toList()))
                .toList());
    }
}
