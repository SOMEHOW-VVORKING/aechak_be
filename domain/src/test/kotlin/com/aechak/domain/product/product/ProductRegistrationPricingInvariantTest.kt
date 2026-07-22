package com.aechak.domain.product.product

import com.aechak.common.error.BusinessException
import com.aechak.domain.product.category.Category
import com.aechak.domain.product.error.ProductErrorCode
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Product.register()가 소유하는 할인 데이터 입력 불변식을 고정한다.
 */
class ProductRegistrationPricingInvariantTest {
    private val now = LocalDateTime.of(2026, 7, 20, 12, 0)

    private fun register(
        regular: Long,
        discount: Long? = null,
        start: LocalDateTime? = null,
        end: LocalDateTime? = null,
    ): Product =
        Product.register(
            category = Category.create(null, 1, "강아지", null, 1),
            sellerId = 1L,
            name = "상품",
            description = null,
            representativeImageKey = null,
            regularPrice = regular,
            discountPrice = discount,
            discountStartAt = start,
            discountEndAt = end,
        )

    @Test
    fun `할인가가 있으면 시작일은 필수다`() {
        assertInvalidPeriod { register(regular = 10000L, discount = 7500L) } // 시작일 없음
        assertInvalidPeriod { register(regular = 10000L, discount = 7500L, end = now) } // 종료일만 있고 시작일 없음
    }

    @Test
    fun `종료일 없이 시작일만 있으면 허용된다`() {
        val p = register(regular = 10000L, discount = 7500L, start = now.minusDays(1))
        assertEquals(7500L, p.discountedPriceAt(now))
    }

    @Test
    fun `역전된 할인 기간은 등록을 거부한다`() {
        assertInvalidPeriod { register(regular = 10000L, discount = 7500L, start = now, end = now.minusMinutes(1)) }
    }

    @Test
    fun `할인가 없이 기간만 지정하면 등록을 거부한다`() {
        assertInvalidPeriod { register(regular = 10000L, start = now, end = now.plusDays(1)) } // 시작·종료 둘 다
        assertInvalidPeriod { register(regular = 10000L, start = now) } // 시작만
        assertInvalidPeriod { register(regular = 10000L, end = now.plusDays(1)) } // 종료만
    }

    private fun assertInvalidPeriod(block: () -> Unit) {
        try {
            block()
            throw AssertionError("INVALID_DISCOUNT_PERIOD가 발생해야 한다")
        } catch (e: BusinessException) {
            assertEquals(ProductErrorCode.INVALID_DISCOUNT_PERIOD, e.errorCode)
        }
    }
}
