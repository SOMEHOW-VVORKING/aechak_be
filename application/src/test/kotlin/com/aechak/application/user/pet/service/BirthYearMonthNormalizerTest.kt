package com.aechak.application.user.pet.service

import com.aechak.common.error.BusinessException
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * 계약 — 생년월 입력의 정규화·거절 경계. 깨지면 연도만 보낸 입력이 엉뚱한 달로 저장되거나
 * 미래 생년월이 통과한다.
 */
class BirthYearMonthNormalizerTest {
    private val today = YearMonth.of(2026, 7)
    private val thisMonth = today.toString()
    private val nextMonth = today.plusMonths(1).toString()
    private val nextYear = today.plusYears(1).year.toString()

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
        assertFailsWith<BusinessException> { BirthYearMonthNormalizer.normalize(nextMonth, today) }
    }

    @Test
    fun `이번 달은 허용한다`() {
        assertEquals(thisMonth, BirthYearMonthNormalizer.normalize(thisMonth, today))
    }

    @Test
    fun `연도만 준 미래도 거절한다`() {
        assertFailsWith<BusinessException> { BirthYearMonthNormalizer.normalize(nextYear, today) }
    }
}
