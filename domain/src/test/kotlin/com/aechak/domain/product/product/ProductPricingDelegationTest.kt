package com.aechak.domain.product.product

import com.aechak.domain.product.category.Category
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Product의 가격 메서드가 ProductPricing 정책에 위임하는지 확인한다.
 */
class ProductPricingDelegationTest {
    private val now = LocalDateTime.of(2026, 7, 20, 12, 0)

    private val product: Product =
        Product.register(
            category = Category.create(null, 1, "강아지", null, 1),
            sellerId = 1L,
            name = "상품",
            description = null,
            representativeImageKey = null,
            regularPrice = 20000L,
            discountPrice = 14900L,
            discountStartAt = now.minusDays(1),
            discountEndAt = now.plusDays(1),
        )

    @Test
    fun `상품 가격 계산은 ProductPricing 정책에 위임한다`() {
        val pricing = product.pricing()
        assertEquals(pricing.discountedPriceAt(now), product.discountedPriceAt(now))
        assertEquals(pricing.sellingPriceAt(now), product.sellingPriceAt(now))
        assertEquals(pricing.discountRateAt(now), product.discountRateAt(now))
    }
}
