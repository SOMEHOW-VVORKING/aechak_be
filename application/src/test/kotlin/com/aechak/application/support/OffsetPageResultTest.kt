package com.aechak.application.support

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 계약 테스트 — 파생 페이지 정보(totalPages·hasNext)의 계산 규칙을 고정한다. */
class OffsetPageResultTest {
    @Test
    fun `totalPages는 올림으로 계산한다`() {
        assertEquals(0, page(totalCount = 0).totalPages)
        assertEquals(2, page(totalCount = 40).totalPages)
        assertEquals(3, page(totalCount = 41).totalPages)
    }

    @Test
    fun `hasNext는 다음 페이지 존재 여부다`() {
        assertTrue(page(totalCount = 41, current = 0).hasNext)
        assertTrue(page(totalCount = 41, current = 1).hasNext)
        assertFalse(page(totalCount = 41, current = 2).hasNext)
        assertFalse(page(totalCount = 0, current = 0).hasNext)
    }

    @Test
    fun `map은 페이지 정보를 유지한 채 항목만 변환한다`() {
        val mapped = OffsetPageResult(items = listOf(1, 2), totalCount = 41, page = 1, size = 20).map { it.toString() }
        assertEquals(listOf("1", "2"), mapped.items)
        assertEquals(41, mapped.totalCount)
        assertEquals(1, mapped.page)
        assertEquals(20, mapped.size)
    }

    @Test
    fun `음수 page·0 이하 size·음수 totalCount는 생성 자체를 거부한다`() {
        assertFailsWith<IllegalArgumentException> { page(current = -1) }
        assertFailsWith<IllegalArgumentException> { OffsetPageResult(emptyList<Int>(), totalCount = 0, page = 0, size = 0) }
        assertFailsWith<IllegalArgumentException> { page(totalCount = -1) }
    }

    private fun page(
        totalCount: Long = 0,
        current: Int = 0,
    ): OffsetPageResult<Int> = OffsetPageResult(items = emptyList(), totalCount = totalCount, page = current, size = 20)
}
