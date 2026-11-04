package com.aechak.application.moderation.usecase

import com.aechak.application.moderation.usecase.result.ModerationDecision

interface ModerationUseCase {
    // 글을 유지, 마스킹, 차단 중 하나로 판정
    fun decide(content: String): ModerationDecision

    // 글에 금칙어가 하나라도 있는지 확인
    fun containsBannedWord(text: String): Boolean
}
