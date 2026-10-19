package com.aechak.application.order.claim.port.view

import com.aechak.domain.order.claim.enums.ClaimType

data class SellerClaimTypeCountView(
    val claimType: ClaimType,
    val count: Long,
)
