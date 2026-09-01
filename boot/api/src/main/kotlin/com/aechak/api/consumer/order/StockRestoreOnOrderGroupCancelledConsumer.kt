package com.aechak.api.consumer.order

import com.aechak.api.consumer.KafkaConsumerComponent
import com.aechak.application.messaging.ProcessedMessages
import com.aechak.application.product.product.usecase.ProductStockUseCase
import com.aechak.application.product.product.usecase.command.RestoreStockCommand
import com.aechak.infra.kafka.Topics
import com.aechak.infra.kafka.config.MessagingJacksonConfig.Companion.MESSAGING_OBJECT_MAPPER
import com.aechak.message.Envelope
import com.aechak.message.order.OrderGroupCancelledMessage
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper

@KafkaConsumerComponent
class StockRestoreOnOrderGroupCancelledConsumer(
    @Qualifier(MESSAGING_OBJECT_MAPPER) private val objectMapper: ObjectMapper,
    private val processedMessages: ProcessedMessages,
    private val productStockUseCase: ProductStockUseCase,
) {
    @KafkaListener(topics = [Topics.ORDER], groupId = GROUP)
    @Transactional
    fun onMessage(value: String) {
        val envelope = objectMapper.readValue(value, Envelope::class.java)
        if (envelope.eventType != OrderGroupCancelledMessage::class.simpleName) return
        if (!processedMessages.markProcessed(GROUP, envelope.eventId)) return

        val message = objectMapper.readValue(envelope.payload, OrderGroupCancelledMessage::class.java)
        productStockUseCase.restoreStock(
            RestoreStockCommand(message.items.map { RestoreStockCommand.Item(it.optionCombinationId, it.quantity) }),
        )
    }

    companion object {
        const val GROUP = "product-stock-on-order-group-cancelled"
    }
}
