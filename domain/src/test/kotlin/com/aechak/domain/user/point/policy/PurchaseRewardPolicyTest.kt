package com.aechak.domain.user.point.policy

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

/** 적립률 계약. 깨지면 구매확정마다 지급액이 달라진다. */
class PurchaseRewardPolicyTest {
    @Test
    fun `상품금액의 0_5퍼센트를 적립한다`() {
        assertEquals(50L, PurchaseRewardPolicy.amountFor(10_000L))
    }

    @Test
    fun `1원 미만은 버린다`() {
        assertEquals(97L, PurchaseRewardPolicy.amountFor(19_500L))
        assertEquals(0L, PurchaseRewardPolicy.amountFor(199L))
    }

    @Test
    fun `기준 금액이 0이면 적립액도 0이다`() {
        assertEquals(0L, PurchaseRewardPolicy.amountFor(0L))
    }

    @Test
    fun `음수 기준 금액은 거절한다`() {
        assertFailsWith<IllegalArgumentException> { PurchaseRewardPolicy.amountFor(-1L) }
    }
}
