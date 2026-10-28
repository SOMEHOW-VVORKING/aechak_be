package com.aechak.domain.settlement

import com.aechak.common.error.BusinessException
import com.aechak.domain.settlement.error.SettlementErrorCode
import com.aechak.domain.settlement.settlement.policy.ShippingFeeAllocationPolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * 배송비 안분 계약. 깨지면 품목별 정산 행의 배송비 합이 주문의 셀러 배송비와 어긋나 —
 * 셀러에게 배송비를 덜 주거나 더 준다.
 */
class ShippingFeeAllocationPolicyTest {
    @Test
    fun `나누어 떨어지면 품목마다 같은 금액을 갖는다`() {
        assertEquals(mapOf(10L to 1_500L, 20L to 1_500L), ShippingFeeAllocationPolicy.allocate(3_000L, listOf(10L, 20L)))
    }

    @Test
    fun `나머지는 주문품목 id가 작은 쪽부터 한 원씩 얹고 합은 원래 배송비와 같다`() {
        val allocated = ShippingFeeAllocationPolicy.allocate(1_000L, listOf(30L, 10L, 20L))

        assertEquals(mapOf(10L to 334L, 20L to 333L, 30L to 333L), allocated)
        assertEquals(1_000L, allocated.values.sum())
    }

    @Test
    fun `입력 순서가 달라도 같은 배분이 나온다`() {
        assertEquals(
            ShippingFeeAllocationPolicy.allocate(1_000L, listOf(10L, 20L, 30L)),
            ShippingFeeAllocationPolicy.allocate(1_000L, listOf(30L, 20L, 10L)),
        )
    }

    @Test
    fun `무료배송 주문은 품목마다 0원을 갖는다`() {
        assertEquals(mapOf(10L to 0L, 20L to 0L), ShippingFeeAllocationPolicy.allocate(0L, listOf(10L, 20L)))
    }

    @Test
    fun `음수 배송비는 안분하지 않는다`() {
        val exception = assertFailsWith<BusinessException> { ShippingFeeAllocationPolicy.allocate(-1L, listOf(10L)) }

        assertEquals(SettlementErrorCode.INVALID_SETTLEMENT_AMOUNT, exception.errorCode)
    }

    @Test
    fun `품목이 없으면 안분하지 않는다`() {
        assertFailsWith<BusinessException> { ShippingFeeAllocationPolicy.allocate(3_000L, emptyList()) }
    }

    @Test
    fun `같은 품목이 두 번 들어오면 거절한다`() {
        // 중복을 받아 주면 Map이 접혀 배분 합이 배송비보다 작아진다
        val exception = assertFailsWith<BusinessException> { ShippingFeeAllocationPolicy.allocate(3_000L, listOf(10L, 10L)) }

        assertEquals(SettlementErrorCode.INVALID_SETTLEMENT_AMOUNT, exception.errorCode)
    }
}
