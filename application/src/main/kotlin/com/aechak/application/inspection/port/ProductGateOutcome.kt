package com.aechak.application.inspection.port

data class ProductGateOutcome(
    val productName: String,
    val sellerId: Long,
    /** 위반 판정이거나, 관리자가 반려한 상품에 통과 판정이 왔을 때 true */
    val needsAdminReview: Boolean,
)
