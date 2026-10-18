package com.aechak.application.order.claim.service

/** 주문품목별 환불액과 배송비 환불액. 품목 환불액은 claim_items에 그대로 저장된다. */
data class ClaimAmounts(
    val itemRefunds: Map<Long, Long>,
    val shippingFeeRefund: Long,
) {
    val total: Long get() = itemRefunds.values.sum() + shippingFeeRefund
}
