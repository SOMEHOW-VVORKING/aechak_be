package com.aechak.application.review.support

import com.aechak.common.error.BusinessException
import com.aechak.common.error.CommonErrorCode
import java.time.LocalDateTime
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ReviewCursorCodecTest {
    @Test
    fun `LATEST 커서를 왕복하면 마지막 리뷰 ID가 보존된다`() {
        val encoded =
            ReviewCursorCodec.ProductReviews.encodeLatest(
                productId = 9L,
                photoOnly = true,
                lastReviewId = 42L,
            )

        val decoded =
            ReviewCursorCodec.ProductReviews.decodeLatest(
                encodedCursor = encoded,
                expectedProductId = 9L,
                expectedPhotoOnly = true,
            )

        assertEquals(42L, decoded.lastReviewId)
    }

    @Test
    fun `RATING_DESC 커서를 왕복하면 평점과 마지막 리뷰 ID가 보존된다`() {
        val encoded =
            ReviewCursorCodec.ProductReviews.encodeRatingDesc(
                productId = 9L,
                photoOnly = false,
                lastRating = 3,
                lastReviewId = 7L,
            )

        val decoded =
            ReviewCursorCodec.ProductReviews.decodeRatingDesc(
                encodedCursor = encoded,
                expectedProductId = 9L,
                expectedPhotoOnly = false,
            )

        assertEquals(3, decoded.lastRating)
        assertEquals(7L, decoded.lastReviewId)
    }

    @Test
    fun `다른 정렬의 상품 리뷰 커서는 거절한다`() {
        val latest =
            ReviewCursorCodec.ProductReviews.encodeLatest(
                productId = 1L,
                photoOnly = false,
                lastReviewId = 1L,
            )

        assertCursorRejected {
            ReviewCursorCodec.ProductReviews.decodeRatingDesc(
                encodedCursor = latest,
                expectedProductId = 1L,
                expectedPhotoOnly = false,
            )
        }
    }

    @Test
    fun `다른 상품이나 사진 필터의 커서는 거절한다`() {
        val latest =
            ReviewCursorCodec.ProductReviews.encodeLatest(
                productId = 1L,
                photoOnly = false,
                lastReviewId = 1L,
            )

        assertCursorRejected {
            ReviewCursorCodec.ProductReviews.decodeLatest(latest, expectedProductId = 2L, expectedPhotoOnly = false)
        }
        assertCursorRejected {
            ReviewCursorCodec.ProductReviews.decodeLatest(latest, expectedProductId = 1L, expectedPhotoOnly = true)
        }
    }

    @Test
    fun `범위를 벗어난 평점은 거절한다`() {
        val broken = encodeRaw("r:9:0:6:7")

        assertCursorRejected {
            ReviewCursorCodec.ProductReviews.decodeRatingDesc(
                encodedCursor = broken,
                expectedProductId = 9L,
                expectedPhotoOnly = false,
            )
        }
    }

    @Test
    fun `깨진 상품 리뷰 커서는 거절한다`() {
        assertCursorRejected {
            ReviewCursorCodec.ProductReviews.decodeLatest(
                encodedCursor = "%%%broken%%%",
                expectedProductId = 1L,
                expectedPhotoOnly = false,
            )
        }
    }

    @Test
    fun `written 커서를 왕복하면 lastReviewId가 보존된다`() {
        val encoded = ReviewCursorCodec.MyReviews.encodeWritten(userId = 1L, lastReviewId = 42L)

        assertEquals(
            42L,
            ReviewCursorCodec.MyReviews.decodeWritten(encoded, expectedUserId = 1L).lastReviewId,
            "written 앵커가 왕복에서 보존되지 않았다",
        )
    }

    @Test
    fun `unwritten 커서를 왕복하면 확정 시각과 orderItemId가 보존된다`() {
        val confirmedAt = LocalDateTime.of(2026, 3, 4, 5, 6, 7)

        val encoded =
            ReviewCursorCodec.MyReviews.encodeUnreviewedOrderItem(
                userId = 1L,
                lastConfirmedAt = confirmedAt,
                lastOrderItemId = 7L,
            )

        val decoded = ReviewCursorCodec.MyReviews.decodeUnreviewedOrderItem(encoded, expectedUserId = 1L)
        assertEquals(confirmedAt, decoded.lastConfirmedAt, "확정 시각 앵커가 왕복에서 보존되지 않았다")
        assertEquals(7L, decoded.lastOrderItemId, "orderItemId 앵커가 왕복에서 보존되지 않았다")
    }

    @Test
    fun `unwritten 커서는 마이크로초까지 보존한다`() {
        val confirmedAt = LocalDateTime.of(2026, 3, 4, 5, 6, 7, 123_456_000)

        val encoded =
            ReviewCursorCodec.MyReviews.encodeUnreviewedOrderItem(
                userId = 1L,
                lastConfirmedAt = confirmedAt,
                lastOrderItemId = 1L,
            )

        assertEquals(
            confirmedAt,
            ReviewCursorCodec.MyReviews.decodeUnreviewedOrderItem(encoded, expectedUserId = 1L).lastConfirmedAt,
            "마이크로초가 잘려 앵커가 앞으로 밀렸다",
        )
    }

    @Test
    fun `나노초가 섞여 들어와도 마이크로초로 잘라 앵커가 뒤로 밀리지 않는다`() {
        val withNanos = LocalDateTime.of(2026, 3, 4, 5, 6, 7, 123_456_789)

        val encoded =
            ReviewCursorCodec.MyReviews.encodeUnreviewedOrderItem(
                userId = 1L,
                lastConfirmedAt = withNanos,
                lastOrderItemId = 1L,
            )

        assertEquals(
            LocalDateTime.of(2026, 3, 4, 5, 6, 7, 123_456_000),
            ReviewCursorCodec.MyReviews.decodeUnreviewedOrderItem(encoded, expectedUserId = 1L).lastConfirmedAt,
            "나노초가 남아 DB 값과 비교가 어긋난다",
        )
    }

    @Test
    fun `다른 탭의 커서는 거절한다`() {
        val written = ReviewCursorCodec.MyReviews.encodeWritten(userId = 1L, lastReviewId = 1L)
        val unwritten =
            ReviewCursorCodec.MyReviews.encodeUnreviewedOrderItem(
                userId = 1L,
                lastConfirmedAt = LocalDateTime.now(),
                lastOrderItemId = 1L,
            )

        assertCursorRejected { ReviewCursorCodec.MyReviews.decodeUnreviewedOrderItem(written, expectedUserId = 1L) }
        assertCursorRejected { ReviewCursorCodec.MyReviews.decodeWritten(unwritten, expectedUserId = 1L) }
    }

    @Test
    fun `다른 사용자의 커서는 거절한다`() {
        val written = ReviewCursorCodec.MyReviews.encodeWritten(userId = 1L, lastReviewId = 100L)
        val unwritten =
            ReviewCursorCodec.MyReviews.encodeUnreviewedOrderItem(
                userId = 1L,
                lastConfirmedAt = LocalDateTime.now(),
                lastOrderItemId = 100L,
            )

        assertCursorRejected { ReviewCursorCodec.MyReviews.decodeWritten(written, expectedUserId = 2L) }
        assertCursorRejected { ReviewCursorCodec.MyReviews.decodeUnreviewedOrderItem(unwritten, expectedUserId = 2L) }
    }

    @Test
    fun `깨진 내 리뷰 커서는 거절한다`() {
        assertCursorRejected { ReviewCursorCodec.MyReviews.decodeWritten("%%%broken%%%", expectedUserId = 1L) }
        assertCursorRejected { ReviewCursorCodec.MyReviews.decodeUnreviewedOrderItem("%%%broken%%%", expectedUserId = 1L) }
    }

    @Test
    fun `숫자가 아닌 written 앵커는 거절한다`() {
        val broken = encodeRaw("mw:1:not-a-number")

        assertCursorRejected { ReviewCursorCodec.MyReviews.decodeWritten(broken, expectedUserId = 1L) }
    }

    @Test
    fun `0 이하의 앵커 ID는 거절한다`() {
        val broken = encodeRaw("mw:1:0")

        assertCursorRejected { ReviewCursorCodec.MyReviews.decodeWritten(broken, expectedUserId = 1L) }
    }

    private fun encodeRaw(payload: String): String =
        Base64
            .getUrlEncoder()
            .withoutPadding()
            .encodeToString(payload.toByteArray(Charsets.UTF_8))

    private fun assertCursorRejected(block: () -> Unit) {
        val exception = assertFailsWith<BusinessException>(block = block)
        assertEquals(CommonErrorCode.INVALID_CURSOR, exception.errorCode)
    }
}
