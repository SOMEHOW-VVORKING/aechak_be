package com.aechak.domain.product.product

import com.aechak.common.error.BusinessException
import com.aechak.domain.product.category.Category
import com.aechak.domain.product.error.ProductErrorCode
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 단위 테스트 — Product.register()가 소유하는 입력 불변식을 고정한다. 정가 범위, 이미지 개수, 할인 데이터.
 * 깨지면 요청 DTO를 우회한 입구(배치, 어드민, 컨슈머)로 잘못된 상품이 들어온다.
 */
class ProductRegistrationPricingInvariantTest {
    private val now = LocalDateTime.of(2026, 7, 20, 12, 0)

    private fun register(
        regular: Long,
        discount: Long? = null,
        start: LocalDateTime? = null,
        end: LocalDateTime? = null,
        additionalImageKeys: List<String> = emptyList(),
        detailImageKeys: List<String> = emptyList(),
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
            additionalImageKeys = additionalImageKeys,
            detailImageKeys = detailImageKeys,
        )

    @Test
    fun `정가는 허용 범위 안이어야 한다`() {
        assertEquals(
            Product.REGULAR_PRICE_MIN,
            register(regular = Product.REGULAR_PRICE_MIN).regularPrice,
            "하한 경계값은 허용돼야 한다",
        )
        assertEquals(
            Product.REGULAR_PRICE_MAX,
            register(regular = Product.REGULAR_PRICE_MAX).regularPrice,
            "상한 경계값은 허용돼야 한다",
        )
        assertRejected(ProductErrorCode.INVALID_PRODUCT_PRICE) { register(regular = Product.REGULAR_PRICE_MIN - 1) }
        assertRejected(ProductErrorCode.INVALID_PRODUCT_PRICE) { register(regular = Product.REGULAR_PRICE_MAX + 1) }
    }

    @Test
    fun `이미지 개수는 종류별 상한을 넘을 수 없다`() {
        assertRejected(ProductErrorCode.TOO_MANY_PRODUCT_IMAGES) {
            register(regular = 10000L, additionalImageKeys = keys(Product.ADDITIONAL_IMAGE_MAX + 1))
        }
        assertRejected(ProductErrorCode.TOO_MANY_PRODUCT_IMAGES) {
            register(regular = 10000L, detailImageKeys = keys(Product.DETAIL_IMAGE_MAX + 1))
        }
    }

    @Test
    fun `상한까지는 등록된다`() {
        val product =
            register(
                regular = 10000L,
                additionalImageKeys = keys(Product.ADDITIONAL_IMAGE_MAX),
                detailImageKeys = keys(Product.DETAIL_IMAGE_MAX),
            )
        assertEquals(
            Product.ADDITIONAL_IMAGE_MAX + Product.DETAIL_IMAGE_MAX,
            product.images.size,
            "대표 이미지가 없으면 추가와 상세 이미지 수의 합만 남아야 한다",
        )
    }

    @Test
    fun `할인가가 있으면 시작일은 필수다`() {
        assertInvalidPeriod { register(regular = 10000L, discount = 7500L) } // 시작일 없음
        assertInvalidPeriod { register(regular = 10000L, discount = 7500L, end = now) } // 종료일만 있고 시작일 없음
    }

    @Test
    fun `종료일 없이 시작일만 있으면 허용된다`() {
        val p = register(regular = 10000L, discount = 7500L, start = now.minusDays(1))
        assertEquals(7500L, p.discountedPriceAt(now), "종료일이 없으면 무기한 할인이라 지금도 할인가여야 한다")
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

    private fun keys(count: Int): List<String> = (1..count).map { "products/$it.png" }

    private fun assertInvalidPeriod(block: () -> Unit) = assertRejected(ProductErrorCode.INVALID_DISCOUNT_PERIOD, block)

    private fun assertRejected(
        expected: ProductErrorCode,
        block: () -> Unit,
    ) {
        try {
            block()
            throw AssertionError("$expected 가 발생해야 한다")
        } catch (e: BusinessException) {
            assertEquals(expected, e.errorCode, "다른 코드가 나왔다: ${e.errorCode}")
        }
    }
}
