package com.aechak.infra.kafka.publisher

import com.aechak.infra.kafka.config.MessagingJacksonConfig.Companion.MESSAGING_OBJECT_MAPPER
import com.aechak.message.BestEffortMessage
import com.aechak.message.Envelope
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import tools.jackson.databind.ObjectMapper

@Component
internal class DirectMessagePublisher(
    private val sender: KafkaSender,
    private val envelopes: EnvelopeFactory,
    @Qualifier(MESSAGING_OBJECT_MAPPER) private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun publish(message: BestEffortMessage) {
        val envelope = envelopes.from(message)

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                object : TransactionSynchronization {
                    override fun afterCommit() = send(envelope)
                },
            )
        } else {
            send(envelope)
        }
    }

    private fun send(envelope: Envelope) {
        // send()는 동기로도 던짐(메타데이터 대기 타임아웃 등)
        try {
            sender
                .send(
                    envelope.aggregateType,
                    envelope.orderingKey,
                    envelope.eventId,
                    envelope.eventType,
                    envelope.traceId,
                    objectMapper.writeValueAsString(envelope),
                ).whenComplete { _, e ->
                    if (e != null) logLost(envelope, e)
                }
        } catch (e: Exception) {
            logLost(envelope, e)
        }
    }

    private fun logLost(
        envelope: Envelope,
        e: Throwable,
    ) {
        // 콜백 스레드에는 MDC가 없어 traceId를 로그 인자로 명시함
        log.error(
            "직접 발행 실패 - 유실 (eventType={}, orderingKey={}, eventId={}, traceId={})",
            envelope.eventType,
            envelope.orderingKey,
            envelope.eventId,
            envelope.traceId,
            e,
        )
    }
}
