package com.aechak.application.review.support

import com.aechak.application.review.port.ReviewListSort
import com.aechak.common.error.BusinessException
import com.aechak.common.error.CommonErrorCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ReviewCursorCodecTest {
    @Test
    fun `LATEST 커서를 왕복하면 productId photoOnly lastId가 보존되고 rating은 없다`() {
        val encoded = ReviewCursorCodec.encode(ReviewListSort.LATEST, productId = 9L, photoOnly = true, lastId = 42L, lastRating = 5)

        val decoded = ReviewCursorCodec.decode(encoded, ReviewListSort.LATEST)

        assertEquals(9L, decoded.productId)
        assertEquals(true, decoded.photoOnly)
        assertEquals(42L, decoded.lastId)
        assertNull(decoded.lastRating)
    }

    @Test
    fun `RATING_DESC 커서를 왕복하면 productId photoOnly rating id가 보존된다`() {
        val encoded = ReviewCursorCodec.encode(ReviewListSort.RATING_DESC, productId = 9L, photoOnly = false, lastId = 7L, lastRating = 3)

        val decoded = ReviewCursorCodec.decode(encoded, ReviewListSort.RATING_DESC)

        assertEquals(9L, decoded.productId)
        assertEquals(false, decoded.photoOnly)
        assertEquals(7L, decoded.lastId)
        assertEquals(3, decoded.lastRating)
    }

    @Test
    fun `다른 정렬로 디코딩하면 INVALID_CURSOR로 거절한다`() {
        val latest = ReviewCursorCodec.encode(ReviewListSort.LATEST, productId = 1L, photoOnly = false, lastId = 1L, lastRating = 5)

        assertCursorRejected { ReviewCursorCodec.decode(latest, ReviewListSort.RATING_DESC) }
    }

    @Test
    fun `깨진 커서는 INVALID_CURSOR로 거절한다`() {
        assertCursorRejected { ReviewCursorCodec.decode("%%%broken%%%", ReviewListSort.LATEST) }
    }

    private fun assertCursorRejected(block: () -> Unit) {
        val e = assertFailsWith<BusinessException>(block = block)
        assertEquals(CommonErrorCode.INVALID_CURSOR, e.errorCode)
    }
}
