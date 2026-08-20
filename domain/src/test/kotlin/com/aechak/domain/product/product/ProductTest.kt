package com.aechak.domain.product.product

import com.aechak.common.error.BusinessException
import com.aechak.common.error.CommonErrorCode
import com.aechak.domain.product.category.Category
import com.aechak.domain.product.error.ProductErrorCode
import com.aechak.domain.product.product.enums.ProductImageType
import com.aechak.domain.product.product.enums.SaleStatus
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 단위 테스트. Product가 소유하는 수정 불변식(가격·할인 정합, 이미지 전체 교체, 판매 상태 전이)을 고정한다.
 * 깨지면 등록에서만 막히던 값이 수정으로 들어오거나, 교체한 줄 알았던 이미지가 옛 것과 섞인다.
 */
class ProductTest {
    private val now = LocalDateTime.of(2026, 8, 12, 12, 0)
    private val food = Category.create(null, Category.ROOT_DEPTH, "사료", null, 1)
    private val snack = Category.create(null, Category.ROOT_DEPTH, "간식", null, 2)

    private fun product(
        additionalImageKeys: List<String> = emptyList(),
        detailImageKeys: List<String> = emptyList(),
    ): Product =
        Product.register(
            category = food,
            sellerId = 1L,
            name = "연어 건사료 2kg",
            description = "설명",
            representativeImageKey = "products/old-thumb.png",
            regularPrice = 25_000L,
            discountPrice = 20_000L,
            discountStartAt = now.minusDays(1),
            discountEndAt = null,
            additionalImageKeys = additionalImageKeys,
            detailImageKeys = detailImageKeys,
        )

    private fun Product.applyEdit(
        category: Category = food,
        name: String = "연어 건사료 2kg",
        description: String? = "설명",
        regularPrice: Long = 25_000L,
        discountPrice: Long? = null,
        discountStartAt: LocalDateTime? = null,
        discountEndAt: LocalDateTime? = null,
    ) = edit(category, name, description, regularPrice, discountPrice, discountStartAt, discountEndAt)

    @Test
    fun `정가는 허용 범위 안이어야 한다`() {
        assertEquals(
            Product.REGULAR_PRICE_MIN,
            product().also { it.applyEdit(regularPrice = Product.REGULAR_PRICE_MIN) }.regularPrice,
            "하한 경계값은 허용돼야 한다",
        )
        assertEquals(
            Product.REGULAR_PRICE_MAX,
            product().also { it.applyEdit(regularPrice = Product.REGULAR_PRICE_MAX) }.regularPrice,
            "상한 경계값은 허용돼야 한다",
        )
        assertRejected(ProductErrorCode.INVALID_PRODUCT_PRICE) { product().applyEdit(regularPrice = Product.REGULAR_PRICE_MIN - 1) }
        assertRejected(ProductErrorCode.INVALID_PRODUCT_PRICE) { product().applyEdit(regularPrice = Product.REGULAR_PRICE_MAX + 1) }
    }

    @Test
    fun `할인가는 정가를 넘을 수 없다`() {
        assertRejected(ProductErrorCode.INVALID_PRODUCT_PRICE) {
            product().applyEdit(regularPrice = 25_000L, discountPrice = 25_001L, discountStartAt = now)
        }
        assertRejected(ProductErrorCode.INVALID_PRODUCT_PRICE) {
            product().applyEdit(regularPrice = 25_000L, discountPrice = -1L, discountStartAt = now)
        }
    }

    @Test
    fun `할인가가 있으면 시작일은 필수다`() {
        assertInvalidPeriod { product().applyEdit(discountPrice = 20_000L) }
        assertInvalidPeriod { product().applyEdit(discountPrice = 20_000L, discountEndAt = now) }
    }

    @Test
    fun `역전된 할인 기간은 수정을 거부한다`() {
        assertInvalidPeriod {
            product().applyEdit(discountPrice = 20_000L, discountStartAt = now, discountEndAt = now.minusMinutes(1))
        }
    }

    @Test
    fun `할인가 없이 기간만 지정하면 수정을 거부한다`() {
        assertInvalidPeriod { product().applyEdit(discountStartAt = now) }
        assertInvalidPeriod { product().applyEdit(discountEndAt = now) }
    }

    @Test
    fun `할인 세 필드를 비우면 할인이 해제된다`() {
        val product = product()

        product.applyEdit(discountPrice = null, discountStartAt = null, discountEndAt = null)

        assertNull(product.discountPrice, "할인가가 비워져야 한다")
        assertNull(product.discountStartAt, "할인 시작이 비워져야 한다")
        assertEquals(25_000L, product.sellingPriceAt(now), "할인이 풀렸으니 판매가는 정가여야 한다")
    }

    @Test
    fun `수정은 카테고리와 정보를 함께 바꾼다`() {
        val product = product()

        product.applyEdit(category = snack, name = "닭가슴살 큐브", description = null, regularPrice = 9_900L)

        assertEquals(snack, product.category, "카테고리가 바뀌어야 한다")
        assertEquals("닭가슴살 큐브", product.name, "상품명이 바뀌어야 한다")
        assertNull(product.description, "설명은 null로 지울 수 있어야 한다")
        assertEquals(9_900L, product.regularPrice, "정가가 바뀌어야 한다")
    }

    @Test
    fun `거절된 수정은 어떤 필드도 바꾸지 않는다`() {
        val product = product()

        assertRejected(ProductErrorCode.INVALID_PRODUCT_PRICE) {
            product.applyEdit(category = snack, name = "바뀌면 안 되는 이름", regularPrice = 0L)
        }

        assertEquals(food, product.category, "검증이 대입보다 먼저 돌아야 한다")
        assertEquals("연어 건사료 2kg", product.name, "거절된 수정이 상품명을 남기면 안 된다")
        assertEquals(25_000L, product.regularPrice, "거절된 수정이 정가를 남기면 안 된다")
    }

    @Test
    fun `이미지 교체 후 현재 노출분은 보낸 목록 그대로다`() {
        val product =
            product(
                additionalImageKeys = listOf("products/a1.png", "products/a2.png"),
                detailImageKeys = listOf("products/d1.png"),
            )

        product.replaceImages(
            thumbnailImageKey = "products/new-thumb.png",
            additionalImageKeys = listOf("products/a9.png"),
            detailImageKeys = emptyList(),
            versionNo = 2,
        )

        assertEquals("products/new-thumb.png", product.representativeImageKey, "대표 이미지 캐시가 함께 갱신돼야 한다")
        assertEquals(
            listOf(
                ProductImageType.REPRESENTATIVE to "products/new-thumb.png",
                ProductImageType.PRODUCT to "products/a9.png",
            ),
            product.currentImages.map { it.imageType to it.storageKey },
            "보낸 목록이 곧 현재 노출분이어야 한다: ${product.currentImages.map { it.storageKey }}",
        )
        assertEquals(listOf(0, 0), product.currentImages.map { it.sortOrder }, "종류마다 0부터 다시 매겨야 한다")
    }

    @Test
    fun `빠진 이미지는 지워지지 않고 노출 구간이 닫힌다`() {
        val product = product(additionalImageKeys = listOf("products/a1.png"))

        product.replaceImages(
            thumbnailImageKey = "products/old-thumb.png",
            additionalImageKeys = listOf("products/a9.png"),
            detailImageKeys = emptyList(),
            versionNo = 2,
        )

        val closed = product.images.single { it.storageKey == "products/a1.png" }
        assertEquals(1, closed.fromVersionNo, "등록 버전부터 보였어야 한다")
        assertEquals(1, closed.toVersionNo, "교체 직전 버전에서 닫혀야 한다")
        val opened = product.images.single { it.storageKey == "products/a9.png" }
        assertEquals(2, opened.fromVersionNo, "새 행은 교체 버전부터 열려야 한다")
        assertNull(opened.toVersionNo, "현재 노출분은 열린 채여야 한다")
    }

    @Test
    fun `유지되는 이미지 행은 건드리지 않는다`() {
        val product = product(additionalImageKeys = listOf("products/a1.png"))
        val keptBefore = product.images.single { it.storageKey == "products/a1.png" }

        product.replaceImages(
            thumbnailImageKey = "products/old-thumb.png",
            additionalImageKeys = listOf("products/a1.png"),
            detailImageKeys = listOf("products/d1.png"),
            versionNo = 2,
        )

        val keptAfter = product.images.single { it.storageKey == "products/a1.png" }
        assertTrue(keptBefore === keptAfter, "같은 자리 그대로면 행을 닫았다 다시 열면 안 된다")
        assertNull(keptAfter.toVersionNo, "유지되는 행은 열린 채여야 한다")
        assertEquals(1, keptAfter.fromVersionNo, "유지되는 행의 시작 버전이 바뀌면 안 된다")
    }

    @Test
    fun `순서만 바꾸면 행을 닫지 않고 순서만 고친다`() {
        val product = product(additionalImageKeys = listOf("products/a1.png", "products/a2.png"))
        val before = product.currentImages.associateBy { it.storageKey }

        product.replaceImages(
            thumbnailImageKey = "products/old-thumb.png",
            additionalImageKeys = listOf("products/a2.png", "products/a1.png"),
            detailImageKeys = emptyList(),
            versionNo = 2,
        )

        assertEquals(before.size, product.images.size, "순서 변경은 행을 늘리지 않아야 한다")
        assertTrue(
            product.currentImages.all { it === before[it.storageKey] },
            "같은 이미지면 닫았다 다시 열지 말고 그 행을 그대로 써야 한다",
        )
        assertEquals(
            listOf(1, 0),
            listOf("products/a1.png", "products/a2.png").map { key ->
                product.currentImages.single { it.storageKey == key }.sortOrder
            },
            "보낸 순서대로 sortOrder를 다시 매겨야 한다",
        )
    }

    @Test
    fun `이미지 교체도 종류별 상한을 지킨다`() {
        assertRejected(ProductErrorCode.TOO_MANY_PRODUCT_IMAGES) {
            product().replaceImages("products/t.png", keys(Product.ADDITIONAL_IMAGE_MAX + 1), emptyList(), versionNo = 2)
        }
        assertRejected(ProductErrorCode.TOO_MANY_PRODUCT_IMAGES) {
            product().replaceImages("products/t.png", emptyList(), keys(Product.DETAIL_IMAGE_MAX + 1), versionNo = 2)
        }
    }

    @Test
    fun `상한을 넘긴 교체는 기존 이미지를 건드리지 않는다`() {
        val product = product(additionalImageKeys = listOf("products/a1.png"))

        assertRejected(ProductErrorCode.TOO_MANY_PRODUCT_IMAGES) {
            product.replaceImages("products/new-thumb.png", emptyList(), keys(Product.DETAIL_IMAGE_MAX + 1), versionNo = 2)
        }

        assertEquals("products/old-thumb.png", product.representativeImageKey, "거절된 교체가 대표 키를 바꾸면 안 된다")
        assertEquals(2, product.currentImages.size, "거절된 교체가 노출분을 닫으면 안 된다")
    }

    @Test
    fun `판매 상태는 판매중과 판매중지 사이를 오간다`() {
        val product = product()
        assertEquals(SaleStatus.ON_SALE, product.saleStatus, "등록 직후는 판매중이어야 한다")

        product.changeSaleStatusBySeller(SaleStatus.SUSPENDED)
        assertEquals(SaleStatus.SUSPENDED, product.saleStatus, "판매중지로 내려가야 한다")

        product.changeSaleStatusBySeller(SaleStatus.ON_SALE)
        assertEquals(SaleStatus.ON_SALE, product.saleStatus, "판매중으로 되돌아와야 한다")
    }

    @Test
    fun `셀러는 품절과 판매종료를 지정할 수 없다`() {
        assertRejectedAsInvalidRequest { product().changeSaleStatusBySeller(SaleStatus.OUT_OF_STOCK) }
        assertRejectedAsInvalidRequest { product().changeSaleStatusBySeller(SaleStatus.ENDED) }
    }

    @Test
    fun `거절된 상태 변경은 상태를 남기지 않는다`() {
        val product = product()

        assertRejectedAsInvalidRequest { product.changeSaleStatusBySeller(SaleStatus.OUT_OF_STOCK) }

        assertEquals(SaleStatus.ON_SALE, product.saleStatus, "거절됐으면 판매중 그대로여야 한다")
    }

    private fun keys(count: Int): List<String> = (1..count).map { "products/$it.png" }

    private fun assertInvalidPeriod(block: () -> Unit) = assertRejected(ProductErrorCode.INVALID_DISCOUNT_PERIOD, block)

    private fun assertRejectedAsInvalidRequest(block: () -> Unit) {
        try {
            block()
            throw AssertionError("90001이 발생해야 한다")
        } catch (e: BusinessException) {
            assertEquals(CommonErrorCode.INVALID_REQUEST, e.errorCode, "다른 코드가 나왔다: ${e.errorCode}")
            assertEquals("판매중과 판매중지만 지정할 수 있습니다.", e.message, "거절 사유가 메시지에 실려야 한다")
        }
    }

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
