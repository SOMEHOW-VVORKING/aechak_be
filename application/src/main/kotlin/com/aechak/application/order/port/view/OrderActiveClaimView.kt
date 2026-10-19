package com.aechak.application.order.port.view

import com.aechak.domain.order.claim.enums.ClaimStatus
import com.aechak.domain.order.claim.enums.ClaimType
import com.aechak.domain.order.claim.enums.RefundStatus

/** 주문품목을 아직 붙잡고 있는 클레임 한 건. 거부·철회로 풀린 클레임은 여기 담기지 않는다. */
data class OrderActiveClaimView(
    val claimPublicId: String,
    val claimType: ClaimType,
    val status: ClaimStatus,
    val refundStatus: RefundStatus?,
)
