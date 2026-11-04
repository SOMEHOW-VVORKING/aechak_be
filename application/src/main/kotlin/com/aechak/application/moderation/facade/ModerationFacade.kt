package com.aechak.application.moderation.facade

import com.aechak.application.moderation.service.ModerationService
import com.aechak.application.moderation.usecase.ModerationUseCase
import com.aechak.application.moderation.usecase.result.ModerationDecision
import org.springframework.stereotype.Service

@Service
class ModerationFacade(
    private val moderationService: ModerationService,
) : ModerationUseCase {
    override fun decide(content: String): ModerationDecision = moderationService.decide(content)

    override fun containsBannedWord(text: String): Boolean = moderationService.containsBannedWord(text)
}
