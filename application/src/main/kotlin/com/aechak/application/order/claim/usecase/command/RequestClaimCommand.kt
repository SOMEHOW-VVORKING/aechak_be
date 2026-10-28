package com.aechak.application.order.claim.usecase.command

import com.aechak.domain.order.claim.enums.ClaimReasonCode
import com.aechak.domain.order.claim.enums.ClaimType

/** targets가 비면 주문 전체를 대상으로 본다. */
data class RequestClaimCommand(
    val buyerId: Long,
    val orderPublicId: String,
    val claimType: ClaimType,
    val reasonCode: ClaimReasonCode,
    val reasonDetail: String?,
    val targets: List<ClaimTarget>,
) {
    data class ClaimTarget(
        val orderItemId: Long,
        val quantity: Int,
    )
}
