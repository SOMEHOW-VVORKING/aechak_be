package com.aechak.domain.review.summary

import java.time.Duration
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** [단위] 요청 상태가 범위별 기대 지문을 되돌려 주는지 고정한다. 깨지면 받은 요약이 저장되지 않거나 엉뚱한 지문으로 저장된다 */
class ReviewSummaryRequestTest {
    @Test
    fun `요청에 실은 범위의 기대 지문만 돌려준다`() {
        val request = ReviewSummaryRequest.create(productId = 1)

        request.start("req-1", mapOf("PRODUCT" to "10:55:10", "DOG:DOG_UNDER_5" to "8:36:8"), LocalDateTime.now(), Duration.ofMinutes(30))

        assertEquals("10:55:10", request.findExpectedSignature("PRODUCT"))
        assertEquals("8:36:8", request.findExpectedSignature("DOG:DOG_UNDER_5"))
        assertNull(request.findExpectedSignature("CAT:CAT_4_6"))
        assertTrue(request.isLatest("req-1"))
        assertFalse(request.isLatest("req-0"))
    }
}
