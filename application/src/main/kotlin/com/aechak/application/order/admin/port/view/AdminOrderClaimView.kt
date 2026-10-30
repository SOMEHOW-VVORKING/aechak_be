package com.aechak.application.order.admin.port.view

import com.aechak.domain.order.claim.enums.ClaimStatus
import com.aechak.domain.order.claim.enums.ClaimType

/** 주문에 걸린 진행 중 클레임 한 건. 주문 상태만으로는 반품·교환 진행이 보이지 않아 함께 내린다. */
data class AdminOrderClaimView(
    val orderId: Long,
    val claimId: Long,
    val claimPublicId: String,
    val claimType: ClaimType,
    val status: ClaimStatus,
)
