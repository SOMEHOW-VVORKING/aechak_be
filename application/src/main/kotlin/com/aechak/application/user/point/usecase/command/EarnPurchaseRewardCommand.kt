package com.aechak.application.user.point.usecase.command

/**
 * 구매확정 적립 명세. 기준 금액은 주문 도메인이 계산해 넘기고, 적립률과 원장 기록은 적립금 도메인이 쥔다.
 * orderPublicId가 멱등키의 재료다 — 같은 주문의 재확정 시도는 원장 1행으로 접힌다.
 */
data class EarnPurchaseRewardCommand(
    val buyerUserId: Long,
    val orderId: Long,
    val orderPublicId: String,
    val productAmount: Long,
)
