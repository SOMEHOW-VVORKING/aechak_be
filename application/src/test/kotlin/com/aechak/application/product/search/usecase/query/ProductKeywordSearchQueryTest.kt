package com.aechak.application.product.search.usecase.query

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** 계약 테스트 — 검색 입력 불변식(검색어 비어있음, 최대 길이, size 범위)을 고정 */
class ProductKeywordSearchQueryTest {
    @Test
    fun `기본값과 경계값은 통과한다`() {
        assertEquals(20, ProductKeywordSearchQuery(keyword = "사료").size)
        assertEquals(1, ProductKeywordSearchQuery(keyword = "사료", size = 1).size)
        assertEquals(100, ProductKeywordSearchQuery(keyword = "사료", size = 100).size)
        assertEquals(100, ProductKeywordSearchQuery(keyword = "가".repeat(100)).keyword.length)
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
