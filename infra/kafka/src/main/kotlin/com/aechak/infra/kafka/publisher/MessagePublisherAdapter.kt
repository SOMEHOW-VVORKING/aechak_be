package com.aechak.infra.kafka.publisher

import com.aechak.application.messaging.MessagePublisher
import com.aechak.message.BestEffortMessage
import com.aechak.message.GuaranteedMessage
import com.aechak.message.IntegrationMessage
import org.springframework.stereotype.Component

@Component
internal class MessagePublisherAdapter(
    private val outbox: OutboxMessagePublisher,
    private val direct: DirectMessagePublisher,
) : MessagePublisher {
    override fun publish(message: IntegrationMessage) {
        when (message) {
            is GuaranteedMessage -> outbox.publish(message)
            is BestEffortMessage -> direct.publish(message)
        }
    }
}
