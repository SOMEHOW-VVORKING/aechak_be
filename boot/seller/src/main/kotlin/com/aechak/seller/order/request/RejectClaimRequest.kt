package com.aechak.seller.order.request

import com.aechak.application.order.claim.usecase.command.RejectClaimCommand
import com.aechak.domain.order.claim.Claim
import jakarta.validation.constraints.Size

/** 공백 사유는 형식이 아니라 업무 규칙이라 도메인이 거절한다 — 여기서는 길이만 본다. */
data class RejectClaimRequest(
    @field:Size(max = Claim.REASON_MAX_LENGTH, message = "거부 사유는 500자까지 입력할 수 있습니다.")
    val rejectionReason: String = "",
) {
    fun toCommand(
        sellerId: Long,
        claimPublicId: String,
    ): RejectClaimCommand =
        RejectClaimCommand(
            sellerId = sellerId,
            claimPublicId = claimPublicId,
            rejectionReason = rejectionReason,
        )
}
