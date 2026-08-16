package com.aechak.api.consumer.review

import com.aechak.application.messaging.ProcessedMessages
import com.aechak.application.review.moderation.usecase.ReviewModerationUseCase
import com.aechak.infra.kafka.Topics
import com.aechak.infra.kafka.config.MessagingJacksonConfig.Companion.MESSAGING_OBJECT_MAPPER
import com.aechak.message.Envelope
import com.aechak.message.review.ReviewCreatedMessage
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper

/** 리뷰 작성 이벤트로 내용을 판정해 마스킹하거나 차단 */
@ConditionalOnProperty("spring.kafka.bootstrap-servers")
@Component
class ReviewModerationConsumer(
    @Qualifier(MESSAGING_OBJECT_MAPPER) private val objectMapper: ObjectMapper,
    private val processedMessages: ProcessedMessages,
    private val reviewModerationUseCase: ReviewModerationUseCase,
) {
    @KafkaListener(topics = [Topics.REVIEW], groupId = GROUP)
    @Transactional
    fun onMessage(value: String) {
        val envelope = objectMapper.readValue(value, Envelope::class.java)
        if (envelope.eventType != ReviewCreatedMessage::class.simpleName) return

        if (!processedMessages.markProcessed(GROUP, envelope.eventId)) return

        val message = objectMapper.readValue(envelope.payload, ReviewCreatedMessage::class.java)
        reviewModerationUseCase.moderate(message.reviewId)
    }

    companion object {
        private const val GROUP = "review-moderation"
    }
}
