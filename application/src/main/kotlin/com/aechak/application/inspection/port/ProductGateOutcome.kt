package com.aechak.application.inspection.port

data class ProductGateOutcome(
    val productName: String,
    val sellerId: Long,
    /** 통과 판정이어도 관리자가 반려했던 상품이면 true. 사람이 내린 결정을 판정이 뒤집지 않음 */
    val needsAdminReview: Boolean,
)
