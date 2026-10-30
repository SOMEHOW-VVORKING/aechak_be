package com.aechak.domain.review.summary

import com.aechak.domain.review.summary.enums.AspectPolarity
import com.aechak.domain.review.summary.enums.ReviewExtractionStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [단위] 리뷰 한 건의 추출 상태 전이를 고정한다.
 * 깨지면 재배달된 실패 응답이 확정된 추출을 지우거나, 계속 실패하는 리뷰에 모델 비용을 끝없이 쓴다.
 */
class ReviewAspectExtractionTest {
    @Test
    fun `추출에 성공한 뒤에 온 실패나 다른 추출은 무시한다`() {
        val extraction = ReviewAspectExtraction.create(reviewId = 1, productId = 10)
        extraction.recordExtracted(listOf(ReviewAspect("PALATABILITY", AspectPolarity.PRO, "기호성", "잘 먹어요")), "v1")

        extraction.recordUnextracted(null, "v2")
        extraction.recordExtracted(emptyList(), "v2")

        assertEquals(ReviewExtractionStatus.EXTRACTED, extraction.status)
        assertEquals(1, extraction.aspects.size)
        assertEquals("v1", extraction.extractVersion)
    }

    @Test
    fun `사유 없는 실패는 상한 횟수까지 다시 보낸다`() {
        val extraction = ReviewAspectExtraction.create(reviewId = 1, productId = 10)

        repeat(ReviewAspectExtraction.MAX_FAILED_ATTEMPTS - 1) { extraction.recordUnextracted(null, "v1") }
        assertFalse(extraction.isSettled())

        extraction.recordUnextracted(null, "v1")
        assertTrue(extraction.isSettled())
    }

    @Test
    fun `다시 보내도 같은 사유로 빠진 리뷰는 한 번에 확정한다`() {
        val extraction = ReviewAspectExtraction.create(reviewId = 1, productId = 10)

        extraction.recordUnextracted("INJECTION", "v1")

        assertTrue(extraction.isSettled())
        assertEquals(ReviewExtractionStatus.UNEXTRACTED, extraction.status)
    }
}
