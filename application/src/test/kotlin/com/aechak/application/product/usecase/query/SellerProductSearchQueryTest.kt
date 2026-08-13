package com.aechak.application.product.usecase.query

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** 계약 테스트 — 페이징·키워드·기간 불변식을 생성 시점에 강제한다(HTTP 어댑터 우회 호출 방어). */
class SellerProductSearchQueryTest {
    @Test
    fun `허용 범위의 page·size로 생성된다`() {
        assertEquals(0, SellerProductSearchQuery(sellerId = 1L).page)
        assertEquals(20, SellerProductSearchQuery(sellerId = 1L).size)
        assertEquals(100, SellerProductSearchQuery(sellerId = 1L, size = 100).size)
    }

    @Test
    fun `범위 밖 size와 음수 page는 생성 자체를 거부한다`() {
        assertFailsWith<IllegalArgumentException> { SellerProductSearchQuery(sellerId = 1L, size = 0) }
        assertFailsWith<IllegalArgumentException> { SellerProductSearchQuery(sellerId = 1L, size = 101) }
        assertFailsWith<IllegalArgumentException> { SellerProductSearchQuery(sellerId = 1L, page = -1) }
    }

    @Test
    fun `공백이거나 너무 긴 keyword는 거부한다`() {
        assertFailsWith<IllegalArgumentException> { SellerProductSearchQuery(sellerId = 1L, keyword = " ") }
        assertFailsWith<IllegalArgumentException> { SellerProductSearchQuery(sellerId = 1L, keyword = "가".repeat(101)) }
    }

    @Test
    fun `역전된 등록일 범위는 거부하고 같은 날짜는 허용한다`() {
        val day = LocalDate.of(2026, 8, 1)
        assertFailsWith<IllegalArgumentException> {
            SellerProductSearchQuery(sellerId = 1L, createdFrom = day.plusDays(1), createdTo = day)
        }
        assertEquals(day, SellerProductSearchQuery(sellerId = 1L, createdFrom = day, createdTo = day).createdFrom)
    }
}
