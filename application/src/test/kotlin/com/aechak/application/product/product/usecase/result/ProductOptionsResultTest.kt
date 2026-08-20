package com.aechak.application.product.product.usecase.result

import com.aechak.application.product.product.port.view.ProductOptionsView
import com.aechak.domain.product.option.OptionCombination
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 단위 테스트 — 옵션 응답의 재고 노출 표현(임계치 이하만 수치 공개, 0은 품절)을 고정한다. 규칙 수치는 도메인 상수를 따른다. */
class ProductOptionsResultTest {
    private fun combinationWithStock(stockQuantity: Int): ProductOptionsResult.OptionCombinationResult {
        val view =
            ProductOptionsView(
                optionGroups = emptyList(),
                optionCombinations =
                    listOf(
                        ProductOptionsView.OptionCombinationView(
                            optionCombinationId = 1L,
                            name = "기본",
                            additionalPrice = 0L,
                            optionValueIds = emptyList(),
                            stockQuantity = stockQuantity,
                        ),
                    ),
            )
        return ProductOptionsResult.from(view).optionCombinations.single()
    }

    @Test
    fun `재고가 임계치 이하면 잔여 수량을 그대로 노출한다`() {
        assertEquals(0, combinationWithStock(0).remainingStock)
        assertEquals(1, combinationWithStock(1).remainingStock)
        assertEquals(
            OptionCombination.STOCK_DISPLAY_THRESHOLD,
            combinationWithStock(OptionCombination.STOCK_DISPLAY_THRESHOLD).remainingStock,
        )
    }

    @Test
    fun `재고가 임계치를 넘으면 잔여 수량을 노출하지 않는다`() {
        assertNull(combinationWithStock(OptionCombination.STOCK_DISPLAY_THRESHOLD + 1).remainingStock)
        assertNull(combinationWithStock(999).remainingStock)
    }

    @Test
    fun `재고 0만 품절로 표시한다`() {
        assertTrue(combinationWithStock(0).soldOut)
        assertFalse(combinationWithStock(1).soldOut)
    }
}
