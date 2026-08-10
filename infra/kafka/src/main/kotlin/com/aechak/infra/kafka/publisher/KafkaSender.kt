package com.aechak.infra.kafka.publisher

import com.aechak.infra.kafka.Topics
import com.aechak.infra.kafka.consumer.TraceIdRecordInterceptor
import org.apache.kafka.clients.producer.ProducerRecord
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.SendResult
import org.springframework.stereotype.Component
import java.util.concurrent.CompletableFuture

@Component
internal class KafkaSender(
    private val kafka: KafkaTemplate<String, String>,
) {
    fun send(
        aggregateType: String,
        aggregateId: String,
        eventId: String,
        eventType: String,
        traceId: String,
        payload: String,
    ): CompletableFuture<SendResult<String, String>> {
        val record = ProducerRecord(Topics.of(aggregateType), aggregateId, payload)
        record.headers().add("event_id", eventId.toByteArray())
        record.headers().add("event_type", eventType.toByteArray())
        record.headers().add(TraceIdRecordInterceptor.TRACE_ID_HEADER, traceId.toByteArray())
        return kafka.send(record)
    }
}
