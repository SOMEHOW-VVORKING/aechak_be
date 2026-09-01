package com.aechak.api.consumer.order

import com.aechak.api.consumer.KafkaConsumerComponent
import com.aechak.application.messaging.ProcessedMessages
import com.aechak.application.order.support.OrderPointKeys
import com.aechak.application.user.point.usecase.PointUseCase
import com.aechak.application.user.point.usecase.command.ReleasePointCommand
import com.aechak.infra.kafka.Topics
import com.aechak.infra.kafka.config.MessagingJacksonConfig.Companion.MESSAGING_OBJECT_MAPPER
import com.aechak.message.Envelope
import com.aechak.message.order.OrderGroupCancelledMessage
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper

@KafkaConsumerComponent
class PointReleaseOnOrderGroupCancelledConsumer(
    @Qualifier(MESSAGING_OBJECT_MAPPER) private val objectMapper: ObjectMapper,
    private val processedMessages: ProcessedMessages,
    private val pointUseCase: PointUseCase,
) {
    @KafkaListener(topics = [Topics.ORDER], groupId = GROUP)
    @Transactional
    fun onMessage(value: String) {
        val envelope = objectMapper.readValue(value, Envelope::class.java)
        if (envelope.eventType != OrderGroupCancelledMessage::class.simpleName) return
        if (!processedMessages.markProcessed(GROUP, envelope.eventId)) return

        val message = objectMapper.readValue(envelope.payload, OrderGroupCancelledMessage::class.java)
        if (message.usedPoint == 0L) return

        pointUseCase.releasePoint(
            ReleasePointCommand(
                userId = message.buyerId,
                amount = message.usedPoint,
                idempotencyKey = OrderPointKeys.releaseKey(message.orderGroupPublicId),
                sourceType = OrderPointKeys.SOURCE_TYPE_ORDER_GROUP,
            ),
        )
    }

    companion object {
        const val GROUP = "point-release-on-order-group-cancelled"
    }
}
