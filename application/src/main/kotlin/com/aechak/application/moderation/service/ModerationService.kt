package com.aechak.application.moderation.service

import com.aechak.application.moderation.port.ProfanityScanner
import com.aechak.application.moderation.usecase.result.ModerationDecision
import org.springframework.stereotype.Service

/** 글 판정 정책 */
@Service
class ModerationService(
    private val profanityScanner: ProfanityScanner,
) {
    fun decide(content: String): ModerationDecision {
        val result = profanityScanner.scan(content)
        return when {
            !result.hasMatch -> ModerationDecision.Keep

            // 금지어가 특정 비율 이상인 경우에만 차단, 그 외에는 마스킹
            result.matchedRatio > BLOCK_RATIO_THRESHOLD -> ModerationDecision.Block

            else -> ModerationDecision.Mask(result.maskedContent)
        }
    }

    fun containsBannedWord(text: String): Boolean = profanityScanner.scan(text).hasMatch

    companion object {
        private const val BLOCK_RATIO_THRESHOLD = 0.5
    }
}
