package com.aechak.api.order.claim.response

import com.aechak.application.order.claim.usecase.result.ClaimReasonCatalogResult
import com.aechak.domain.order.claim.enums.ClaimReasonCode
import com.aechak.domain.order.claim.enums.ClaimType

/** 유형별 사유 선택지. 코드·문구·상세 필수 여부를 함께 내려 화면이 선택지를 조립할 수 있게 한다. */
data class ClaimReasonCatalogResponse(
    val claimTypes: List<ClaimTypeReasonsResponse>,
) {
    data class ClaimTypeReasonsResponse(
        val claimType: ClaimType,
        val reasons: List<ClaimReasonResponse>,
    )

    data class ClaimReasonResponse(
        val code: ClaimReasonCode,
        val label: String,
        val detailRequired: Boolean,
    )

    companion object {
        fun from(result: ClaimReasonCatalogResult): ClaimReasonCatalogResponse =
            ClaimReasonCatalogResponse(
                claimTypes =
                    result.reasonsByClaimType.map { (claimType, reasons) ->
                        ClaimTypeReasonsResponse(
                            claimType = claimType,
                            reasons =
                                reasons.map {
                                    ClaimReasonResponse(code = it, label = it.label, detailRequired = it.requiresDetail())
                                },
                        )
                    },
            )
    }
}
