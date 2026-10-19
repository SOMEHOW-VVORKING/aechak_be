package com.aechak.application.order.claim.service

/** 주문품목별 환불액과 배송비 환불액. 품목 환불액은 claim_items에 그대로 저장된다. */
data class ClaimAmounts(
    val itemRefunds: Map<Long, Long>,
    val shippingFeeRefund: Long,
    /** 취소 대상 줄에 안분돼 있던 적립금 — 현금으로 나가지 않았으므로 환불이 아니라 적립금으로 되돌린다 */
    val pointRefund: Long,
) {
    // 합산이 Long 범위를 넘기면 조용히 음수가 된 환불액을 저장하는 대신 터뜨린다
    val total: Long get() = Math.addExact(itemRefunds.values.fold(0L, Math::addExact), shippingFeeRefund)
}
