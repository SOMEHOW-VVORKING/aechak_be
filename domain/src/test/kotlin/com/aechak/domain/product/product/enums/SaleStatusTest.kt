package com.aechak.domain.product.product.enums

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * SaleStatus 구매 가능 판정 테스트
 * 판매중만 구매 가능하고 나머지는 불가
 */
class SaleStatusTest {
    @Test
    fun `판매중이면 구매 가능하다`() {
        assertTrue(SaleStatus.ON_SALE.canPurchase())
    }

    @Test
    fun `품절_판매중지_판매종료는 구매 불가하다`() {
        assertFalse(SaleStatus.OUT_OF_STOCK.canPurchase())
        assertFalse(SaleStatus.SUSPENDED.canPurchase())
        assertFalse(SaleStatus.ENDED.canPurchase())
    }

    @Test
    fun `노출 가능한 판매 상태는 판매중과 품절뿐이다`() {
        assertEquals(setOf(SaleStatus.ON_SALE, SaleStatus.OUT_OF_STOCK), SaleStatus.EXPOSABLE)
    }
}
