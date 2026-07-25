package com.aechak.application.product.usecase.query

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** 계약 테스트 — 페이지 크기 불변식(1..100)을 생성 시점에 강제한다(HTTP 어댑터 우회 호출 방어). */
class ProductSearchQueryTest {
    @Test
    fun `허용 범위의 size로 생성된다`() {
        assertEquals(1, ProductSearchQuery(size = 1).size)
        assertEquals(100, ProductSearchQuery(size = 100).size)
        assertEquals(20, ProductSearchQuery().size)
    }

    @Test
    fun `범위 밖 size는 생성 자체를 거부한다`() {
        assertFailsWith<IllegalArgumentException> { ProductSearchQuery(size = 0) }
        assertFailsWith<IllegalArgumentException> { ProductSearchQuery(size = 101) }
        assertFailsWith<IllegalArgumentException> { ProductSearchQuery(size = Int.MAX_VALUE) } // size + 1 오버플로 경로 차단
    }
}
