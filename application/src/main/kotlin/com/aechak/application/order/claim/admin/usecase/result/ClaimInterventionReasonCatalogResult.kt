package com.aechak.application.order.claim.admin.usecase.result

import com.aechak.domain.order.claim.enums.ClaimFaultDecision
import com.aechak.domain.order.claim.enums.ClaimInterventionReason

/** 귀책 판정별로 고를 수 있는 개입 사유 어휘. */
data class ClaimInterventionReasonCatalogResult(
    val reasonsByFaultDecision: Map<ClaimFaultDecision, List<ClaimInterventionReason>>,
) {
    companion object {
        fun of(): ClaimInterventionReasonCatalogResult =
            ClaimInterventionReasonCatalogResult(
                reasonsByFaultDecision = ClaimFaultDecision.entries.associateWith { ClaimInterventionReason.selectableFor(it) },
            )
    }
}
