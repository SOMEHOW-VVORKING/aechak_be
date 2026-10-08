package com.aechak.application.review.moderation.service

import com.aechak.application.review.moderation.port.ProfanityScanner
import org.springframework.stereotype.Service

/** 리뷰 내용 판정 정책 */
@Service
class ReviewModerationService(
    private val profanityScanner: ProfanityScanner,
) {
    fun decide(content: String): ReviewModerationDecision {
        val result = profanityScanner.scan(content)
        return when {
            !result.hasMatch -> ReviewModerationDecision.Keep

            // 금지어가 특정 비율 이상인 경우에만 차단, 그 외에는 마스킹
            result.matchedRatio > BLOCK_RATIO_THRESHOLD -> ReviewModerationDecision.Block

            else -> ReviewModerationDecision.Mask(result.maskedContent)
        }
    }

    companion object {
        private const val BLOCK_RATIO_THRESHOLD = 0.5
    }
}
