package com.aechak.domain.product.option

import com.aechak.common.error.BusinessException
import com.aechak.domain.product.error.ProductErrorCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 단위 테스트 — 그룹 목록과 조합 목록을 함께 봐야 판정되는 규칙을 고정한다.
 * 깨지면 화면에서 선택할 수 없는 조합이나 찾을 수 없는 조합이 저장되어, 등록은 되는데 팔리지 않는 상품이 생긴다.
 */
class ProductOptionsTest {
    @Test
    fun `그룹마다 값을 하나씩 지목한 조합은 통과한다`() {
        val options =
            ProductOptions.of(
                groups = listOf(group("색상", "레드", "블랙"), group("사이즈", "S", "M")),
                combinations = listOf(combination("레드", "S"), combination("블랙", "M")),
            )

        assertEquals(2, options.groups.size, "그룹 둘을 그대로 담아야 한다")
        assertEquals(2, options.combinations.size, "조합 둘을 그대로 담아야 한다")
    }

    @Test
    fun `옵션을 쓰지 않으면 값이 빈 조합 하나로 성립한다`() {
        val options = ProductOptions.of(groups = emptyList(), combinations = listOf(combination()))

        assertTrue(options.groups.isEmpty(), "그룹은 없어야 한다")
        val onlyCombination = options.combinations.single()
        assertTrue(onlyCombination.valueNames.isEmpty(), "기본 조합은 옵션값을 지목하지 않는다")
    }

    @Test
    fun `조합이 하나도 없으면 거절한다`() {
        assertRejected("판매와 재고 단위") {
            ProductOptions.of(groups = emptyList(), combinations = emptyList())
        }
    }

    @Test
    fun `그룹명이 겹치면 거절한다`() {
        assertRejected("그룹명") {
            ProductOptions.of(
                groups = listOf(group("색상", "레드"), group("색상", "블랙")),
                combinations = listOf(combination("레드")),
            )
        }
    }

    @Test
    fun `그룹이 달라도 옵션값 이름이 겹치면 거절한다`() {
        assertRejected("옵션값 이름") {
            ProductOptions.of(
                groups = listOf(group("색상", "S"), group("사이즈", "S")),
                combinations = listOf(combination("S", "S")),
            )
        }
    }

    @Test
    fun `그룹에 없는 옵션값을 지목하면 거절한다`() {
        assertRejected("그룹에 없는") {
            ProductOptions.of(
                groups = listOf(group("색상", "레드")),
                combinations = listOf(combination("빨강")),
            )
        }
    }

    @Test
    fun `한 그룹에서 값을 둘 고르면 거절한다`() {
        // 서명 UNIQUE는 통과하지만 화면에서 선택될 수 없는 조합이 된다
        assertRejected("하나씩") {
            ProductOptions.of(
                groups = listOf(group("색상", "레드", "블랙"), group("사이즈", "S")),
                combinations = listOf(combination("레드", "블랙")),
            )
        }
    }

    @Test
    fun `그룹을 덜 고른 조합은 거절한다`() {
        // 구매자가 두 그룹을 다 고르면 이 조합을 찾지 못한다
        assertRejected("하나씩") {
            ProductOptions.of(
                groups = listOf(group("색상", "레드"), group("사이즈", "S")),
                combinations = listOf(combination("레드")),
            )
        }
    }

    @Test
    fun `같은 옵션값 조합을 두 번 보내면 거절한다`() {
        assertRejected("두 번") {
            ProductOptions.of(
                groups = listOf(group("색상", "레드", "블랙")),
                combinations = listOf(combination("레드"), combination("레드")),
            )
        }
    }

    private fun group(
        name: String,
        vararg valueNames: String,
    ) = ProductOptions.GroupSpec(name, valueNames.toList())

    private fun combination(vararg valueNames: String) =
        ProductOptions.CombinationSpec(valueNames.toList(), additionalPrice = 0L, stockQuantity = 10)

    private fun assertRejected(
        messagePart: String,
        block: () -> Unit,
    ) {
        try {
            block()
            throw AssertionError("$messagePart 사유로 거절돼야 한다")
        } catch (e: BusinessException) {
            assertEquals(ProductErrorCode.INVALID_PRODUCT_OPTIONS, e.errorCode, "옵션 구성 오류 코드로 나가야 한다")
            assertTrue(
                e.message!!.contains(messagePart),
                "어느 규칙이 깨졌는지 메시지에 실려야 한다. 실제: ${e.message}",
            )
        }
    }
}
