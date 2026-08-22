package com.aechak.domain.product.product.enums

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * SaleStatus 판정 단위 테스트. 구매 가능 여부와 노출 대상, 셀러가 직접 지정할 수 있는 값을 고정한다.
 * 깨지면 못 사는 상품이 구매 가능으로 열리거나, 셀러가 재고에서 파생하는 품절을 손으로 찍어 재고와 어긋난다.
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

    @Test
    fun `셀러가 지정할 수 있는 값은 판매중과 판매중지뿐이다`() {
        assertEquals(
            listOf(SaleStatus.ON_SALE, SaleStatus.SUSPENDED),
            SaleStatus.entries.filter { it.isSellerAssignable() },
            "품절은 재고 파생이고 판매종료는 도달 경로가 없어 셀러가 못 찍는다",
        )
    }
}
