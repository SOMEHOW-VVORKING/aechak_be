package com.aechak.application.order.seller.port.view

import com.aechak.domain.order.claim.enums.ClaimStatus
import com.aechak.domain.order.claim.enums.ClaimType

/** 주문에 걸린 클레임 한 건. 화면 어휘로 접지 않고 BE 상태를 그대로 내린다. */
data class SellerOrderClaimView(
    val orderId: Long,
    val claimId: Long,
    val claimPublicId: String,
    val claimType: ClaimType,
    val status: ClaimStatus,
)
