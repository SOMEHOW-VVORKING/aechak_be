package com.aechak.domain.user.point.policy

/** 구매확정 적립 정책. 기준 금액은 배송비를 뺀 상품금액이고, 1원 미만은 버린다. */
object PurchaseRewardPolicy {
    /** 0.5%. 비율을 1,000분율 정수로 둬서 부동소수 반올림 없이 내림한다. */
    private const val RATE_PER_MILLE = 5L
    private const val MILLE = 1_000L

    fun amountFor(productAmount: Long): Long {
        require(productAmount >= 0) { "적립 기준 금액이 음수입니다 (productAmount=$productAmount)" }
        return Math.multiplyExact(productAmount, RATE_PER_MILLE) / MILLE
    }
}
