package com.aechak.infra.kafka.publisher

import com.aechak.infra.kafka.config.MessagingJacksonConfig.Companion.MESSAGING_OBJECT_MAPPER
import com.aechak.infra.kafka.consumer.TraceIdRecordInterceptor
import com.aechak.message.Envelope
import com.aechak.message.IntegrationMessage
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import java.util.UUID

/** eventId 등은 메시지가 가진 값을 그대로 옮김. 여기서 만들면 다시 보낼 때 키가 달라짐 */
@Component
internal class EnvelopeFactory(
    @Value("\${spring.application.name}") private val producer: String,
    @Qualifier(MESSAGING_OBJECT_MAPPER) private val objectMapper: ObjectMapper,
) {
    fun from(message: IntegrationMessage): Envelope {
        val eventId = message.eventId
        val occurredAt = message.occurredAt
        val aggregateType = message.aggregateType
        val aggregateId = message.aggregateId
        return Envelope(
            eventId = eventId,
            eventType = message::class.simpleName!!, // 컨슈머 라우팅 기준은 계약 클래스명. 도메인 이벤트명 아님
            occurredAt = occurredAt,
            aggregateType = aggregateType,
            aggregateId = aggregateId,
            traceId = MDC.get(TraceIdRecordInterceptor.TRACE_ID_MDC_KEY) ?: UUID.randomUUID().toString(),
            producer = producer,
            payload = objectMapper.writeValueAsString(message),
        )
    }
}
