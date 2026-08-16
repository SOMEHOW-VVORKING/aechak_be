package com.aechak.api.consumer.review

import com.aechak.application.messaging.ProcessedMessages
import com.aechak.application.product.stats.usecase.ProductStatsUseCase
import com.aechak.infra.kafka.Topics
import com.aechak.infra.kafka.config.MessagingJacksonConfig.Companion.MESSAGING_OBJECT_MAPPER
import com.aechak.message.Envelope
import com.aechak.message.review.ReviewCreatedMessage
import com.aechak.message.review.ReviewDeletedMessage
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper

/** 리뷰 작성, 삭제, 차단 이벤트로 product_stats의 평점 통계를 재집계 */
@ConditionalOnProperty("spring.kafka.bootstrap-servers")
@Component
class ReviewRatingProjectionConsumer(
    @Qualifier(MESSAGING_OBJECT_MAPPER) private val objectMapper: ObjectMapper,
    private val processedMessages: ProcessedMessages,
    private val productStatsUseCase: ProductStatsUseCase,
) {
    @KafkaListener(topics = [Topics.REVIEW], groupId = GROUP)
    @Transactional
    fun onMessage(value: String) {
        val envelope = objectMapper.readValue(value, Envelope::class.java)
        val productId = productIdOf(envelope) ?: return

        if (!processedMessages.markProcessed(GROUP, envelope.eventId)) return

        productStatsUseCase.recomputeReviewStats(productId)
    }

    private fun productIdOf(envelope: Envelope): Long? =
        when (envelope.eventType) {
            ReviewCreatedMessage::class.simpleName -> {
                objectMapper.readValue(envelope.payload, ReviewCreatedMessage::class.java).productId
            }

            ReviewDeletedMessage::class.simpleName -> {
                objectMapper.readValue(envelope.payload, ReviewDeletedMessage::class.java).productId
            }

            else -> {
                null
            }
        }

    companion object {
        private const val GROUP = "product-stats-projector"
    }
}
