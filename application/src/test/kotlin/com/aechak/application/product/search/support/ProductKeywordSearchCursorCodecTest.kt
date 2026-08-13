package com.aechak.application.product.search.support

import com.aechak.application.product.search.port.ProductKeywordFilter
import com.aechak.application.product.search.port.ProductKeywordSearchSort
import com.aechak.common.error.BusinessException
import com.aechak.common.error.CommonErrorCode
import java.time.LocalDateTime
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

/** 검색 커서 계약 테스트. 인코딩 후 디코딩 값 보존, 필터 해시, 위조나 불일치 입력의 400 거절 */
class ProductKeywordSearchCursorCodecTest {
    private val publicId = "01JABCDEFGHJKMNPQRSTVWXYZ0"
    private val now = LocalDateTime.of(2026, 8, 5, 12, 0, 0)
    private val filter =
        ProductKeywordFilter(
            keyword = "사료",
            minPrice = null,
            maxPrice = null,
            minRating = null,
            categoryId = null,
            freeShipping = false,
            excludeSoldOut = false,
        )
    private val hash = ProductKeywordSearchCursorCodec.filterHash(filter)

    private fun crafted(payload: String): String = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.toByteArray())

    private fun assertInvalidCursor(block: () -> Unit) {
        try {
            block()
            throw AssertionError("INVALID_CURSOR가 발생해야 한다")
        } catch (e: BusinessException) {
            assertEquals(CommonErrorCode.INVALID_CURSOR, e.errorCode)
        }
    }

    @Test
    fun `인기순 커서는 해시, publicId, 리뷰수, 앵커시각을 왕복 보존한다`() {
        val raw =
            ProductKeywordSearchCursorCodec.encode(
                ProductKeywordSearchSort.POPULAR,
                hash,
                publicId,
                lastReviewCount = 7,
                lastPrice = null,
                now = now,
            )
        val decoded = ProductKeywordSearchCursorCodec.decode(raw, ProductKeywordSearchSort.POPULAR)
        assertEquals(hash, decoded.filterHash)
        assertEquals(publicId, decoded.publicId)
        assertEquals(7, decoded.lastReviewCount)
        assertNull(decoded.lastPrice)
        assertEquals(now, decoded.anchorNow)
    }

    @Test
    fun `가격순 커서는 해시, publicId, 가격, 앵커시각을 왕복 보존한다`() {
        val raw =
            ProductKeywordSearchCursorCodec.encode(
                ProductKeywordSearchSort.PRICE_ASC,
                hash,
                publicId,
                lastReviewCount = null,
                lastPrice = 12900,
                now = now,
            )
        val decoded = ProductKeywordSearchCursorCodec.decode(raw, ProductKeywordSearchSort.PRICE_ASC)
        assertEquals(12900, decoded.lastPrice)
        assertNull(decoded.lastReviewCount)
        assertEquals(now, decoded.anchorNow)
    }

    @Test
    fun `최신순 커서는 해시, publicId, 앵커시각을 왕복 보존한다`() {
        val raw =
            ProductKeywordSearchCursorCodec.encode(
                ProductKeywordSearchSort.LATEST,
                hash,
                publicId,
                lastReviewCount = null,
                lastPrice = null,
                now = now,
            )
        val decoded = ProductKeywordSearchCursorCodec.decode(raw, ProductKeywordSearchSort.LATEST)
        assertEquals(hash, decoded.filterHash)
        assertEquals(publicId, decoded.publicId)
        assertNull(decoded.lastReviewCount)
        assertNull(decoded.lastPrice)
        assertEquals(now, decoded.anchorNow)
    }

    @Test
    fun `해시는 필터가 같으면 같고 다르면 다르다`() {
        assertEquals(hash, ProductKeywordSearchCursorCodec.filterHash(filter.copy()))
        assertNotEquals(hash, ProductKeywordSearchCursorCodec.filterHash(filter.copy(minPrice = 1000L)))
        assertNotEquals(hash, ProductKeywordSearchCursorCodec.filterHash(filter.copy(keyword = "간식")))
        assertNotEquals(hash, ProductKeywordSearchCursorCodec.filterHash(filter.copy(freeShipping = true)))
    }

    @Test
    fun `base64가 아니면 거절한다`() {
        assertInvalidCursor { ProductKeywordSearchCursorCodec.decode("%%%not-base64%%%", ProductKeywordSearchSort.POPULAR) }
    }

    @Test
    fun `다른 정렬로 만든 커서는 거절한다`() {
        val popular = ProductKeywordSearchCursorCodec.encode(ProductKeywordSearchSort.POPULAR, hash, publicId, 7, null, now)
        assertInvalidCursor { ProductKeywordSearchCursorCodec.decode(popular, ProductKeywordSearchSort.PRICE_ASC) }
        assertInvalidCursor { ProductKeywordSearchCursorCodec.decode(popular, ProductKeywordSearchSort.LATEST) }
    }

    @Test
    fun `잘못된 태그는 거절한다`() {
        assertInvalidCursor {
            ProductKeywordSearchCursorCodec.decode(
                crafted("x:$hash:7:1000:$publicId"),
                ProductKeywordSearchSort.POPULAR,
            )
        }
    }

    @Test
    fun `파트 수가 다르면 거절한다`() {
        assertInvalidCursor { ProductKeywordSearchCursorCodec.decode(crafted("r:$hash:7:$publicId"), ProductKeywordSearchSort.POPULAR) }
    }

    @Test
    fun `빈 publicId는 거절한다`() {
        assertInvalidCursor { ProductKeywordSearchCursorCodec.decode(crafted("r:$hash:7:1000:"), ProductKeywordSearchSort.POPULAR) }
    }

    @Test
    fun `음수 리뷰수 앵커는 거절한다`() {
        assertInvalidCursor {
            ProductKeywordSearchCursorCodec.decode(
                crafted("r:$hash:-1:1000:$publicId"),
                ProductKeywordSearchSort.POPULAR,
            )
        }
    }

    @Test
    fun `0 이하 앵커시각은 거절한다`() {
        assertInvalidCursor { ProductKeywordSearchCursorCodec.decode(crafted("r:$hash:7:0:$publicId"), ProductKeywordSearchSort.POPULAR) }
    }
}
