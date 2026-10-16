package com.aechak.domain.product.option

import com.aechak.domain.product.category.Category
import com.aechak.domain.product.option.event.OptionCombinationChangedEvent
import com.aechak.domain.product.product.Product
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 단위 테스트. 조합의 재고 증감 계산과 변경 이벤트 수집을 고정함.
 * 깨지면 재고가 음수나 감싸인 값으로 저장되거나, 재고를 바꾼 사실이 상품 리스너에 닿지 않아
 * 품절 자동 전환이 멈춤.
 */
class OptionCombinationTest {
    private val food = Category.create(null, Category.ROOT_DEPTH, "사료", null, 1)

    private fun combination(): OptionCombination {
        val product =
            Product.register(
                category = food,
                sellerId = 1L,
                name = "연어 건사료 2kg",
                description = null,
                representativeImageKey = "products/thumb.png",
                regularPrice = 25_000L,
                discountPrice = null,
                discountStartAt = null,
                discountEndAt = null,
            )
        return OptionCombination.create(
            product = product,
            name = "2kg",
            additionalPrice = 0L,
            stockQuantity = 30,
            valueSignature = "1",
        )
    }

    @Test
    fun `증감이 잔량 안이면 요청한 만큼 그대로 반영한다`() {
        val combination = combination()

        assertEquals(-10, combination.changeStock(-10), "반영량은 요청한 증감량과 같아야 한다")
        assertEquals(20, combination.stockQuantity, "재고가 요청만큼 줄어야 한다")
        assertEquals(5, combination.changeStock(5), "증가도 요청한 만큼 반영돼야 한다")
        assertEquals(25, combination.stockQuantity, "재고가 요청만큼 늘어야 한다")
    }

    @Test
    fun `잔량보다 큰 감소는 0에서 멈추고 반영된 만큼만 돌려준다`() {
        val combination = combination()

        val applied = combination.changeStock(-31)

        assertEquals(-30, applied, "가진 만큼만 반영돼야 요청대로 다 못 뺐다는 걸 알 수 있다")
        assertEquals(0, combination.stockQuantity, "재고가 음수로 내려가면 안 된다")
    }

    @Test
    fun `Int 최솟값 감소도 0에서 멈춘다`() {
        val combination = combination()

        val applied = combination.changeStock(Int.MIN_VALUE)

        assertEquals(-30, applied, "부호 반전이 감싸여도 가진 만큼만 빠져야 한다")
        assertEquals(0, combination.stockQuantity, "재고가 음수로 내려가면 안 된다")
    }

    @Test
    fun `Int 최댓값을 넘는 증가는 최댓값에서 멈춘다`() {
        val combination = combination()

        val applied = combination.changeStock(Int.MAX_VALUE)

        assertEquals(Int.MAX_VALUE - 30, applied, "덧셈이 감싸여 음수가 되면 안 된다")
        assertEquals(Int.MAX_VALUE, combination.stockQuantity, "재고가 Int 범위 안에 남아야 한다")
    }

    @Test
    fun `조합이 바뀐 사실을 이벤트로 수집한다`() {
        val combination = combination()

        combination.registerChangedEvent()

        assertEquals(1, combination.events.size, "조합 변경 이벤트가 정확히 하나 수집돼야 한다")
        assertTrue(combination.events.single() is OptionCombinationChangedEvent, "수집된 이벤트는 조합 변경 이벤트여야 한다")
    }

    @Test
    fun `clearEvents는 수집된 이벤트를 비운다`() {
        val combination = combination()
        combination.registerChangedEvent()

        combination.clearEvents()

        assertTrue(combination.events.isEmpty(), "발행 후 비워야 같은 이벤트가 두 번 나가지 않는다")
    }
}
