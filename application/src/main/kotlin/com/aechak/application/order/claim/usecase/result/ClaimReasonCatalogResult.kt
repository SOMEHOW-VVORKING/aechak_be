package com.aechak.application.order.claim.usecase.result

import com.aechak.domain.order.claim.enums.ClaimReasonCode
import com.aechak.domain.order.claim.enums.ClaimType

/** 클레임 유형별로 선택할 수 있는 사유 어휘. */
data class ClaimReasonCatalogResult(
    val reasonsByClaimType: Map<ClaimType, List<ClaimReasonCode>>,
) {
    companion object {
        fun of(): ClaimReasonCatalogResult =
            ClaimReasonCatalogResult(
                reasonsByClaimType = ClaimType.entries.associateWith { ClaimReasonCode.selectableFor(it) },
            )
    }
}
