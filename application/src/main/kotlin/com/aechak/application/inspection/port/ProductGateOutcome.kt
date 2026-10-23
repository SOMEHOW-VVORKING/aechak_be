package com.aechak.application.inspection.port

data class ProductGateOutcome(
    val productName: String,
    val sellerId: Long,
    /**
     * 통과 판정이어도 사람이 봐야 하면 true.
     *
     * 관리자가 반려했던 상품의 통과(사람이 내린 결정을 판정이 뒤집지 않음)와, 증거가 없는
     * 통과(모델이 그 내용을 읽은 적이 없음)가 여기로 온다.
     */
    val needsAdminReview: Boolean,
)
