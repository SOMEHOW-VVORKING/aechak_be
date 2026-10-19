package com.aechak.application.order.claim.port.view

/** 상품명은 주문 시점 product_versions 스냅샷. */
data class ClaimItemProductView(
    val orderItemId: Long,
    val productName: String,
)
