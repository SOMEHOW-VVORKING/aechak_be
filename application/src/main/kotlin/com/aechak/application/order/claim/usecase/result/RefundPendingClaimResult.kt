package com.aechak.application.order.claim.usecase.result

import com.aechak.domain.order.claim.Claim

/** 환불 집행 스캔 결과 — 집행은 공개 식별자로 다시 로딩한다. */
data class RefundPendingClaimResult(
    val claimPublicId: String,
) {
    companion object {
        fun from(claim: Claim): RefundPendingClaimResult = RefundPendingClaimResult(claim.publicId)
    }
}
