package com.aechak.application.user.pet.service

import com.aechak.common.error.BusinessException
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class BirthYearMonthNormalizerTest {
    private val today = YearMonth.of(2026, 7)

    @Test
    fun `연월을 주면 그대로 쓴다`() {
        assertEquals("2022-04", BirthYearMonthNormalizer.normalize("2022-04", today))
    }

    @Test
    fun `연도만 주면 1월로 맞춘다`() {
        assertEquals("2022-01", BirthYearMonthNormalizer.normalize("2022", today))
    }

    @Test
    fun `안 주면 null 그대로다`() {
        assertNull(BirthYearMonthNormalizer.normalize(null, today))
    }

    @Test
    fun `미래 생년월은 거절한다`() {
        assertFailsWith<BusinessException> { BirthYearMonthNormalizer.normalize("2026-08", today) }
    }

    @Test
    fun `이번 달은 허용한다`() {
        assertEquals("2026-07", BirthYearMonthNormalizer.normalize("2026-07", today))
    }

    @Test
    fun `연도만 준 미래도 거절한다`() {
        assertFailsWith<BusinessException> { BirthYearMonthNormalizer.normalize("2027", today) }
    }
}
