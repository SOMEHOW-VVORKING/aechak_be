package com.aechak.application.support

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** 계약 테스트 — of()의 보정 범위와 생성자 자체의 거부 범위를 고정한다. */
class PageQueryTest {
    @Test
    fun `of는 음수 page를 0으로 끌어올린다`() {
        assertEquals(0, PageQuery.of(-1, 20).page)
        assertEquals(0, PageQuery.of(0, 20).page)
        assertEquals(3, PageQuery.of(3, 20).page)
    }

    @Test
    fun `of는 size를 1 이상 maxSize 이하로 절삭한다`() {
        assertEquals(1, PageQuery.of(0, 0).size)
        assertEquals(1, PageQuery.of(0, -5).size)
        assertEquals(PageQuery.DEFAULT_MAX_SIZE, PageQuery.of(0, 999).size)
        assertEquals(20, PageQuery.of(0, 20).size)
    }

    @Test
    fun `of의 maxSize는 호출부가 더 좁게 줄 수 있다`() {
        assertEquals(10, PageQuery.of(0, 999, maxSize = 10).size)
        assertEquals(5, PageQuery.of(0, 5, maxSize = 10).size)
    }

    @Test
    fun `생성자는 보정하지 않고 범위 밖 값을 거부한다`() {
        assertFailsWith<IllegalArgumentException> { PageQuery(page = -1, size = 20) }
        assertFailsWith<IllegalArgumentException> { PageQuery(page = 0, size = 0) }
    }
}
