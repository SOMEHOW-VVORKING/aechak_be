package com.aechak.domain.review.review

import com.aechak.common.error.BusinessException
import com.aechak.domain.review.error.ReviewErrorCode
import com.aechak.domain.review.review.enums.ReviewBlindActor
import com.aechak.domain.review.review.enums.ReviewBlindReason
import com.aechak.domain.review.review.enums.ReviewStatus
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReviewTest {
    private fun aReview(rating: Int = 5): Review =
        Review.write(
            productId = 1L,
            optionNameSnapshot = "블랙 / L",
            orderItemId = 1L,
            authorUserId = 1L,
            rating = rating,
            content = "좋은 상품입니다",
        )

    @Test
    fun `작성하면 PUBLIC 상태이고 삭제 시각이 없다`() {
        val review = aReview()

        assertEquals(ReviewStatus.PUBLIC, review.reviewStatus)
        assertNull(review.deletedAt)
    }

    @Test
    fun `별점이 상한 5를 넘으면 작성할 수 없다`() {
        val e = assertFailsWith<BusinessException> { aReview(rating = 6) }

        assertEquals(ReviewErrorCode.INVALID_REVIEW_RATING, e.errorCode)
    }

    @Test
    fun `별점이 하한 1 미만이면 작성할 수 없다`() {
        val e = assertFailsWith<BusinessException> { aReview(rating = 0) }

        assertEquals(ReviewErrorCode.INVALID_REVIEW_RATING, e.errorCode)
    }

    @Test
    fun `별점 경계값 1과 5는 허용된다`() {
        assertEquals(1, aReview(rating = 1).rating)
        assertEquals(5, aReview(rating = 5).rating)
    }

    @Test
    fun `사진을 첨부해 작성하면 이미지가 순서대로 담긴다`() {
        val review =
            Review.write(
                productId = 1L,
                optionNameSnapshot = "블랙 / L",
                orderItemId = 1L,
                authorUserId = 1L,
                rating = 5,
                content = "좋은 상품입니다",
                images = listOf(ReviewImage.of("k0", 0), ReviewImage.of("k1", 1)),
            )

        assertEquals(2, review.images.size)
        assertEquals(listOf(0, 1), review.images.map { it.sortOrder })
    }

    @Test
    fun `사진이 상한 5장을 넘으면 작성할 수 없다`() {
        val tooMany = (0..5).map { ReviewImage.of("k$it", it) }

        val e =
            assertFailsWith<BusinessException> {
                Review.write(
                    productId = 1L,
                    optionNameSnapshot = "블랙 / L",
                    orderItemId = 1L,
                    authorUserId = 1L,
                    rating = 5,
                    content = "좋은 상품입니다",
                    images = tooMany,
                )
            }

        assertEquals(ReviewErrorCode.REVIEW_TOO_MANY_IMAGES, e.errorCode)
    }

    @Test
    fun `마스킹하면 MASKED 상태와 노출 문구를 저장한다`() {
        val review = aReview()

        review.mask("** 상품입니다")

        assertEquals(ReviewStatus.MASKED, review.reviewStatus)
        assertEquals("** 상품입니다", review.displayContent)
    }

    @Test
    fun `차단하면 BLOCKED 상태가 되고 자동 판정이 주체로 기록된다`() {
        val review = aReview()

        review.block()

        assertEquals(ReviewStatus.BLOCKED, review.reviewStatus)
        assertEquals(ReviewBlindActor.AI, review.blindActor)
        assertEquals(ReviewBlindReason.PROFANITY, review.blindReasonCode)
        assertNotNull(review.blindedAt)
        assertNull(review.blindedByAdminId, "자동 판정에는 집행한 운영자가 없다")
    }

    @Test
    fun `운영자가 블라인드하면 주체와 사유, 집행자가 기록된다`() {
        val review = aReview()

        review.blindByAdmin(adminId = 77L, reasonCode = ReviewBlindReason.AD_SPAM, reasonText = "  광고 문구 반복  ")

        assertEquals(ReviewStatus.BLOCKED, review.reviewStatus)
        assertEquals(ReviewBlindActor.ADMIN, review.blindActor)
        assertEquals(ReviewBlindReason.AD_SPAM, review.blindReasonCode)
        assertEquals("광고 문구 반복", review.blindReasonText, "메모는 앞뒤 공백을 다듬어 저장한다")
        assertEquals(77L, review.blindedByAdminId)
        assertNotNull(review.blindedAt)
    }

    @Test
    fun `마스킹된 리뷰도 블라인드할 수 있다`() {
        val review = aReview()
        review.mask("** 상품입니다")

        review.blindByAdmin(adminId = 1L, reasonCode = ReviewBlindReason.ABUSE, reasonText = null)

        assertEquals(ReviewStatus.BLOCKED, review.reviewStatus)
        assertEquals("** 상품입니다", review.displayContent, "해제할 때 되돌릴 상태의 근거라 대체 본문은 지우지 않는다")
    }

    @Test
    fun `이미 블라인드된 리뷰를 다시 블라인드하면 REVIEW_ALREADY_BLINDED 오류를 던진다`() {
        val review = aReview()
        review.blindByAdmin(1L, ReviewBlindReason.ABUSE, null)

        val e = assertFailsWith<BusinessException> { review.blindByAdmin(2L, ReviewBlindReason.AD_SPAM, null) }

        assertEquals(ReviewErrorCode.REVIEW_ALREADY_BLINDED, e.errorCode)
        assertEquals(1L, review.blindedByAdminId, "거절된 요청이 먼저 걸린 블라인드의 집행자를 덮어쓰지 않아야 한다")
    }

    @Test
    fun `삭제된 리뷰는 블라인드할 수 없다`() {
        val review = aReview()
        review.delete()

        val e = assertFailsWith<BusinessException> { review.blindByAdmin(1L, ReviewBlindReason.ABUSE, null) }

        assertEquals(ReviewErrorCode.INVALID_REVIEW_STATUS_TRANSITION, e.errorCode)
        assertEquals(ReviewStatus.DELETED, review.reviewStatus)
        assertNull(review.blindActor, "거절된 요청이 처리 주체를 남기지 않아야 한다")
    }

    @Test
    fun `사유 메모가 상한을 넘으면 INVALID_REVIEW_BLIND_REASON 오류를 던진다`() {
        val review = aReview()

        val e =
            assertFailsWith<BusinessException> {
                review.blindByAdmin(1L, ReviewBlindReason.OTHER, "가".repeat(Review.BLIND_REASON_TEXT_MAX + 1))
            }

        assertEquals(ReviewErrorCode.INVALID_REVIEW_BLIND_REASON, e.errorCode)
        assertEquals(ReviewStatus.PUBLIC, review.reviewStatus, "검증 실패는 상태를 바꾸지 않아야 한다")
    }

    @Test
    fun `블라인드를 해제하면 PUBLIC으로 돌아가고 처리 기록이 비워진다`() {
        val review = aReview()
        review.blindByAdmin(5L, ReviewBlindReason.PRIVACY, "주소 노출")

        review.unblind()

        assertEquals(ReviewStatus.PUBLIC, review.reviewStatus)
        assertNull(review.blindActor)
        assertNull(review.blindReasonCode)
        assertNull(review.blindReasonText)
        assertNull(review.blindedAt)
        assertNull(review.blindedByAdminId)
    }

    @Test
    fun `마스킹된 리뷰의 블라인드를 해제하면 MASKED로 돌아간다`() {
        val review = aReview()
        review.mask("** 상품입니다")
        review.blindByAdmin(5L, ReviewBlindReason.ABUSE, null)

        review.unblind()

        assertEquals(ReviewStatus.MASKED, review.reviewStatus, "금칙어 마스킹은 블라인드 해제가 되돌리는 대상이 아니다")
    }

    @Test
    fun `자동 블라인드도 운영자가 해제할 수 있다`() {
        val review = aReview()
        review.block()

        review.unblind()

        assertEquals(ReviewStatus.PUBLIC, review.reviewStatus)
        assertNull(review.blindActor)
    }

    @Test
    fun `블라인드가 아닌 리뷰를 해제하면 REVIEW_NOT_BLINDED 오류를 던진다`() {
        listOf<Pair<String, (Review) -> Unit>>(
            "노출 중" to { },
            "삭제됨" to { it.delete() },
        ).forEach { (label, setUp) ->
            val review = aReview()
            setUp(review)

            val e = assertFailsWith<BusinessException> { review.unblind() }

            assertEquals(ReviewErrorCode.REVIEW_NOT_BLINDED, e.errorCode, "$label 리뷰는 해제 대상이 아니다")
        }
    }

    @Test
    fun `운영자가 삭제하면 집행자와 사유가 남고 블라인드 시각은 비워진다`() {
        val review = aReview()
        review.blindByAdmin(5L, ReviewBlindReason.ABUSE, "욕설")

        review.deleteByAdmin(adminId = 9L, reasonCode = ReviewBlindReason.AD_SPAM, reasonText = "광고")

        assertEquals(ReviewStatus.DELETED, review.reviewStatus)
        assertNotNull(review.deletedAt)
        assertEquals(9L, review.deletedByAdminId)
        assertEquals(ReviewBlindActor.ADMIN, review.blindActor)
        assertEquals(ReviewBlindReason.AD_SPAM, review.blindReasonCode, "삭제 사유가 블라인드 사유를 대체한다")
        assertEquals("광고", review.blindReasonText)
        assertNull(review.blindedAt, "삭제는 블라인드가 아니라 종결이다 — 시각은 deletedAt이 든다")
    }

    @Test
    fun `운영자가 삭제한 리뷰를 다시 삭제하면 INVALID_REVIEW_STATUS_TRANSITION 오류를 던진다`() {
        val review = aReview()
        review.deleteByAdmin(9L, ReviewBlindReason.OTHER, null)

        val e = assertFailsWith<BusinessException> { review.deleteByAdmin(10L, ReviewBlindReason.OTHER, null) }

        assertEquals(ReviewErrorCode.INVALID_REVIEW_STATUS_TRANSITION, e.errorCode)
        assertEquals(9L, review.deletedByAdminId, "거절된 요청이 먼저 지운 운영자를 덮어쓰지 않아야 한다")
    }

    @Test
    fun `작성자가 지운 리뷰에는 운영자 집행 기록이 남지 않는다`() {
        val review = aReview()

        review.delete()

        assertNull(review.deletedByAdminId, "작성자 삭제와 운영자 삭제를 가르는 축이다")
        assertNull(review.blindActor)
    }

    @Test
    fun `삭제하면 DELETED 상태가 되고 삭제 시각이 채워진다`() {
        val review = aReview()

        review.delete()

        assertEquals(ReviewStatus.DELETED, review.reviewStatus)
        assertNotNull(review.deletedAt)
    }

    @Test
    fun `DELETED 리뷰를 다시 삭제하면 INVALID_REVIEW_STATUS_TRANSITION 오류를 던진다`() {
        val review = aReview()
        review.delete()

        val e = assertFailsWith<BusinessException> { review.delete() }

        assertEquals(ReviewErrorCode.INVALID_REVIEW_STATUS_TRANSITION, e.errorCode)
    }

    @Test
    fun `마스킹된 리뷰는 대체 본문을, 대체 본문이 없으면 블라인드 문구를 노출한다`() {
        assertEquals(
            "이 **럼아",
            Review.visibleContent(ReviewStatus.MASKED, content = "이 씨발럼아", displayContent = "이 **럼아"),
        )
        assertEquals(
            "블라인드 처리된 리뷰입니다.",
            Review.visibleContent(ReviewStatus.MASKED, content = "이 씨발럼아", displayContent = null),
        )
    }

    @Test
    fun `마스킹이 아닌 상태는 본문을 그대로 노출한다`() {
        listOf(ReviewStatus.PUBLIC, ReviewStatus.BLOCKED, ReviewStatus.HIDDEN, ReviewStatus.DELETED).forEach { status ->
            assertEquals(
                "원문입니다",
                Review.visibleContent(status, content = "원문입니다", displayContent = "대체 본문"),
                "$status 는 본문을 그대로 둬야 한다",
            )
        }
    }

    @Test
    fun `작성 마감은 구매확정일로부터 30일째 되는 날의 끝이다`() {
        val confirmedAt = LocalDateTime.of(2026, 1, 1, 12, 0)

        assertEquals(LocalDateTime.of(2026, 1, 31, 23, 59, 59, 999_999_999), Review.writeDeadline(confirmedAt))
    }

    @Test
    fun `같은 날 구매확정이면 시각이 달라도 마감이 같다`() {
        val deadline = Review.writeDeadline(LocalDateTime.of(2026, 1, 1, 0, 0))

        assertEquals(deadline, Review.writeDeadline(LocalDateTime.of(2026, 1, 1, 23, 59, 59)))
    }

    @Test
    fun `마감일 오후에도 작성할 수 있고 자정을 넘기면 못 쓴다`() {
        val confirmedAt = LocalDateTime.of(2026, 1, 1, 12, 0)
        val deadline = Review.writeDeadline(confirmedAt)

        assertTrue(Review.isWithinWriteWindow(confirmedAt, confirmedAt))
        assertTrue(Review.isWithinWriteWindow(confirmedAt, LocalDateTime.of(2026, 1, 31, 23, 0)))
        assertTrue(Review.isWithinWriteWindow(confirmedAt, deadline))
        assertFalse(Review.isWithinWriteWindow(confirmedAt, deadline.plusNanos(1)))
    }
}
