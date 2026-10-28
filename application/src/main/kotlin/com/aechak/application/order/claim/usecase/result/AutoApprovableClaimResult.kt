package com.aechak.application.order.claim.usecase.result

import com.aechak.domain.order.claim.Claim
import java.time.LocalDateTime

/** 자동승인 스캔 결과 — 승인은 공개 식별자로 다시 로딩한다. */
data class AutoApprovableClaimResult(
    val claimPublicId: String,
    val autoApproveDueAt: LocalDateTime,
) {
    companion object {
        fun from(claim: Claim): AutoApprovableClaimResult =
            AutoApprovableClaimResult(
                claimPublicId = claim.publicId,
                autoApproveDueAt = requireNotNull(claim.autoApproveDueAt) { "자동승인 스캔은 기한이 있는 건만 돌려준다" },
            )
    }
}
