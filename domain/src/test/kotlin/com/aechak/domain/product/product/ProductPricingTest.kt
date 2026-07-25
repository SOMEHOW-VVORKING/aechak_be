package com.aechak.domain.product.product

import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * ProductPricing 순수 정책 테스트 — 특정 시각의 유효가격 선택, 판매가, 할인율을 고정한다.
 */
class ProductPricingTest {
    private val now = LocalDateTime.of(2026, 7, 20, 12, 0)

    private fun pricing(
        regular: Long,
        discount: Long? = null,
        start: LocalDateTime? = null,
        end: LocalDateTime? = null,
    ): ProductPricing = ProductPricing(regular, discount, start, end)

    @Test
    fun `할인가가 없으면 판매가는 정가이고 할인율은 없다`() {
        val p = pricing(regular = 10000L)
        assertNull(p.discountedPriceAt(now))
        assertEquals(10000L, p.sellingPriceAt(now))
        assertNull(p.discountRateAt(now))
    }

    @Test
    fun `종료일이 없는 할인은 시작 이후 무기한 적용된다`() {
        val p = pricing(regular = 10000L, discount = 7500L, start = now.minusDays(1))
        assertEquals(7500L, p.discountedPriceAt(now))
        assertEquals(7500L, p.sellingPriceAt(now))
    }

    @Test
    fun `할인 시작 전에는 정가, 시작 정각부터 할인가다`() {
        val notStarted = pricing(regular = 10000L, discount = 7500L, start = now.plusMinutes(1), end = now.plusDays(1))
        assertNull(notStarted.discountedPriceAt(now))
        assertEquals(10000L, notStarted.sellingPriceAt(now))

        val startsNow = pricing(regular = 10000L, discount = 7500L, start = now, end = now.plusDays(1))
        assertEquals(7500L, startsNow.discountedPriceAt(now))
    }

    @Test
    fun `할인 종료 정각까지 할인가, 이후에는 정가다`() {
        val endsNow = pricing(regular = 10000L, discount = 7500L, start = now.minusDays(1), end = now)
        assertEquals(7500L, endsNow.discountedPriceAt(now))

        val ended = pricing(regular = 10000L, discount = 7500L, start = now.minusDays(1), end = now.minusMinutes(1))
        assertNull(ended.discountedPriceAt(now))
        assertEquals(10000L, ended.sellingPriceAt(now))
    }

    @Test
    fun `할인율은 반올림한 정수 퍼센트다`() {
        assertEquals(25, pricing(regular = 10000L, discount = 7500L, start = now.minusDays(1)).discountRateAt(now))
        // 25.5% → 26 — 내림이 아니라 반올림임을 고정
        assertEquals(26, pricing(regular = 20000L, discount = 14900L, start = now.minusDays(1)).discountRateAt(now))
    }

    @Test
    fun `정가 0원이면 할인율은 계산하지 않는다`() {
        val p = pricing(regular = 0L, discount = 0L, start = now.minusDays(1))
        assertEquals(0L, p.sellingPriceAt(now))
        assertNull(p.discountRateAt(now))
    }
}
