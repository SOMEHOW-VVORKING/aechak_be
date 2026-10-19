package com.aechak.application.user.point.usecase.command

import com.aechak.domain.user.point.PointTransaction
import com.aechak.domain.user.point.enums.PointTransactionType
import com.aechak.domain.user.user.User

/**
 * 구매확정 적립 명세. 기준 금액은 주문 도메인이 계산해 넘기고, 적립률과 원장 기록은 적립금 도메인이 쥔다.
 * orderPublicId가 멱등키의 재료다 — 같은 주문의 재확정 시도는 원장 1행으로 접힌다.
 */
data class EarnPurchaseRewardCommand(
    val buyerUserId: Long,
    val orderId: Long,
    val orderPublicId: String,
    val productAmount: Long,
) {
    /** 필드 옮겨 담기만. 적립액과 멱등키는 서비스가 정책·규칙으로 계산해 넘긴다. */
    fun toEntity(
        buyer: User,
        amount: Long,
        idempotencyKey: String,
    ): PointTransaction =
        PointTransaction.record(
            buyer = buyer,
            amount = amount,
            transactionType = PointTransactionType.EARN,
            idempotencyKey = idempotencyKey,
            sourceType = SOURCE_TYPE,
            sourceId = orderId,
        )

    private companion object {
        private const val SOURCE_TYPE = "ORDER"
    }
}
