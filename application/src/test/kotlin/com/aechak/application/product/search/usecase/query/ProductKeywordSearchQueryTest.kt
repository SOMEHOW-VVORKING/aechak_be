package com.aechak.application.product.search.usecase.query

import com.aechak.application.product.search.port.ProductKeywordSearchSort
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** 검색 입력 계약 테스트. 검색어, 길이, size, 가격 범위, 별점 범위, 기본 정렬 불변식 */
class ProductKeywordSearchQueryTest {
    @Test
    fun `기본값과 경계값은 통과한다`() {
        assertEquals(20, ProductKeywordSearchQuery(keyword = "사료").size)
        assertEquals(1, ProductKeywordSearchQuery(keyword = "사료", size = 1).size)
        assertEquals(100, ProductKeywordSearchQuery(keyword = "사료", size = 100).size)
        assertEquals(100, ProductKeywordSearchQuery(keyword = "가".repeat(100)).keyword.length)
        assertEquals(ProductKeywordSearchSort.POPULAR, ProductKeywordSearchQuery(keyword = "사료").sort)
    }

    @Test
    fun `가격 범위가 뒤집히면 거절한다`() {
        assertFailsWith<IllegalArgumentException> { ProductKeywordSearchQuery(keyword = "사료", minPrice = 5000L, maxPrice = 1000L) }
    }

    @Test
    fun `음수 가격은 거절한다`() {
        assertFailsWith<IllegalArgumentException> { ProductKeywordSearchQuery(keyword = "사료", minPrice = -1L) }
        assertFailsWith<IllegalArgumentException> { ProductKeywordSearchQuery(keyword = "사료", maxPrice = -1L) }
    }

    @Test
    fun `별점이 범위를 벗어나면 거절한다`() {
        assertFailsWith<IllegalArgumentException> { ProductKeywordSearchQuery(keyword = "사료", minRating = BigDecimal("5.5")) }
        assertFailsWith<IllegalArgumentException> { ProductKeywordSearchQuery(keyword = "사료", minRating = BigDecimal("-0.1")) }
    }

    @Test
    fun `빈 검색어는 거절한다`() {
        assertFailsWith<IllegalArgumentException> { ProductKeywordSearchQuery(keyword = "") }
        assertFailsWith<IllegalArgumentException> { ProductKeywordSearchQuery(keyword = "   ") }
    }

    @Test
    fun `최대 길이를 넘는 검색어는 거절한다`() {
        assertFailsWith<IllegalArgumentException> { ProductKeywordSearchQuery(keyword = "가".repeat(101)) }
    }

    @Test
    fun `size가 범위를 벗어나면 거절한다`() {
        assertFailsWith<IllegalArgumentException> { ProductKeywordSearchQuery(keyword = "사료", size = 0) }
        assertFailsWith<IllegalArgumentException> { ProductKeywordSearchQuery(keyword = "사료", size = 101) }
    }
}
