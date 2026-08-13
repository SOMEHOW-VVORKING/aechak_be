package com.aechak.domain.product.product.enums

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 단위 테스트. 셀러가 직접 지정할 수 있는 판매 상태 목록을 고정한다.
 * 깨지면 셀러가 재고에서 파생하는 품절을 손으로 찍어 재고와 어긋나거나, 도달 경로가 없어야 할 판매종료로 내려간다.
 */
class SaleStatusTest {
    @Test
    fun `셀러가 지정할 수 있는 값은 판매중과 판매중지뿐이다`() {
        assertEquals(
            listOf(SaleStatus.ON_SALE, SaleStatus.SUSPENDED),
            SaleStatus.entries.filter { it.isSellerAssignable() },
            "품절은 재고 파생이고 판매종료는 도달 경로가 없어 셀러가 못 찍는다",
        )
    }
}
