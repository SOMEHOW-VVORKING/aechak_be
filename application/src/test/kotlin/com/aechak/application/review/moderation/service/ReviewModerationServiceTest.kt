package com.aechak.application.review.moderation.service

import com.aechak.application.review.moderation.port.ProfanityScanResult
import com.aechak.application.review.moderation.port.ProfanityScanner
import kotlin.test.Test
import kotlin.test.assertIs

/** 판정 정책 단위 테스트. 스캐너 결과를 고정해 유지, 마스킹, 차단 경계와 50% 임계를 검증한다. */
class ReviewModerationServiceTest {
    private fun serviceReturning(result: ProfanityScanResult) = ReviewModerationService(ProfanityScanner { result })

    @Test
    fun `매치가 없으면 유지한다`() {
        val service = serviceReturning(ProfanityScanResult(hasMatch = false, maskedContent = "깨끗한 글입니다", matchedRatio = 0.0))
        assertIs<ReviewModerationDecision.Keep>(service.decide("깨끗한 글입니다"))
    }

    @Test
    fun `매치가 있고 비율이 임계 이하면 마스킹한다`() {
        val service = serviceReturning(ProfanityScanResult(hasMatch = true, maskedContent = "** 배송은 좋아요", matchedRatio = 0.3))
        assertIs<ReviewModerationDecision.Mask>(service.decide("시발 배송은 좋아요"))
    }

    @Test
    fun `비율이 정확히 절반이면 마스킹한다`() {
        val service = serviceReturning(ProfanityScanResult(hasMatch = true, maskedContent = "**가나", matchedRatio = 0.5))
        assertIs<ReviewModerationDecision.Mask>(service.decide("시발가나"))
    }

    @Test
    fun `비율이 절반을 넘으면 차단한다`() {
        val service = serviceReturning(ProfanityScanResult(hasMatch = true, maskedContent = "**야", matchedRatio = 0.67))
        assertIs<ReviewModerationDecision.Block>(service.decide("시발야"))
    }
}
