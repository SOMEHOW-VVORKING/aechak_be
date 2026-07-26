package com.aechak.application.product.support

import com.aechak.application.product.port.ProductCatalogSort
import com.aechak.common.error.BusinessException
import com.aechak.common.error.CommonErrorCode
import java.time.LocalDateTime
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** 계약 테스트 — 커서 라운드트립(encode→decode)과 위조·불일치 입력의 400 거절을 고정한다. */
class ProductCursorCodecTest {
    private val now = LocalDateTime.of(2026, 7, 20, 12, 0)
    private val publicId = "01JABCDEFGHJKMNPQRSTVWXYZ0"

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
    fun `latest 커서는 카테고리와 publicId를 라운드트립한다`() {
        val cursor =
            ProductCursorCodec.encode(
                ProductCatalogSort.LATEST,
                categoryId = 5L,
                publicId = publicId,
                sortPriceAtAnchor = 0L,
                now = now,
            )
        val decoded = ProductCursorCodec.decode(cursor, ProductCatalogSort.LATEST)
        assertEquals(5L, decoded.categoryId)
        assertEquals(publicId, decoded.publicId)
        assertNull(decoded.lastPrice)
        assertNull(decoded.anchorNow)
    }

    @Test
    fun `무필터 커서의 카테고리는 null로 라운드트립한다`() {
        val cursor =
            ProductCursorCodec.encode(
                ProductCatalogSort.LATEST,
                categoryId = null,
                publicId = publicId,
                sortPriceAtAnchor = 0L,
                now = now,
            )
        assertNull(ProductCursorCodec.decode(cursor, ProductCatalogSort.LATEST).categoryId)
    }

    @Test
    fun `PRICE_ASC 커서는 카테고리와 정렬 기준 가격과 기준 시각과 publicId를 라운드트립한다`() {
        val cursor =
            ProductCursorCodec.encode(
                ProductCatalogSort.PRICE_ASC,
                categoryId = null,
                publicId = publicId,
                sortPriceAtAnchor = 7500L,
                now = now,
            )
        val decoded = ProductCursorCodec.decode(cursor, ProductCatalogSort.PRICE_ASC)
        assertNull(decoded.categoryId)
        assertEquals(publicId, decoded.publicId)
        assertEquals(7500L, decoded.lastPrice)
        assertEquals(now, decoded.anchorNow)
    }

    @Test
    fun `base64가 아닌 문자열은 거절한다`() {
        assertInvalidCursor { ProductCursorCodec.decode("%%%not-base64%%%", ProductCatalogSort.LATEST) }
    }

    @Test
    fun `다른 정렬의 커서는 거절한다`() {
        val latestCursor = ProductCursorCodec.encode(ProductCatalogSort.LATEST, null, publicId, 0L, now)
        assertInvalidCursor { ProductCursorCodec.decode(latestCursor, ProductCatalogSort.PRICE_ASC) }

        val priceCursor = ProductCursorCodec.encode(ProductCatalogSort.PRICE_ASC, null, publicId, 7500L, now)
        assertInvalidCursor { ProductCursorCodec.decode(priceCursor, ProductCatalogSort.LATEST) }
    }

    @Test
    fun `파트 수가 다른 페이로드는 거절한다`() {
        assertInvalidCursor {
            ProductCursorCodec.decode(
                crafted("l:5"),
                ProductCatalogSort.LATEST,
            )
        } // 카테고리 토큰만, publicId 없음
        // 구버전 price_asc 포맷 — 카테고리 토큰이 없으므로 거절
        assertInvalidCursor {
            ProductCursorCodec.decode(
                crafted("p:100:1752980400000:$publicId"),
                ProductCatalogSort.PRICE_ASC,
            )
        }
    }

    @Test
    fun `잘못된 카테고리 토큰은 거절한다`() {
        assertInvalidCursor { ProductCursorCodec.decode(crafted("l:notnum:$publicId"), ProductCatalogSort.LATEST) }
        assertInvalidCursor { ProductCursorCodec.decode(crafted("l:0:$publicId"), ProductCatalogSort.LATEST) } // 양수 아님
    }

    @Test
    fun `음수 가격 페이로드는 거절한다`() {
        assertInvalidCursor {
            ProductCursorCodec.decode(
                crafted("p:all:-1:1752980400000:$publicId"),
                ProductCatalogSort.PRICE_ASC,
            )
        }
    }

    @Test
    fun `숫자가 아닌 기준 시각 페이로드는 거절한다`() {
        assertInvalidCursor {
            ProductCursorCodec.decode(
                crafted("p:all:100:notmillis:$publicId"),
                ProductCatalogSort.PRICE_ASC,
            )
        }
    }
}
