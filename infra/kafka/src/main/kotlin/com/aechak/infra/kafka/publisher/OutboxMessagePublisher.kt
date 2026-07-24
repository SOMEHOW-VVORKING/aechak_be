package com.aechak.infra.kafka.publisher

import com.aechak.application.messaging.MessagePublisher
import com.aechak.domain.support.DomainEvent
import com.aechak.infra.kafka.config.MessagingJacksonConfig.Companion.MESSAGING_OBJECT_MAPPER
import com.aechak.message.Envelope
import com.aechak.message.IntegrationMessage
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import java.time.Instant
import java.util.UUID

@Component
class OutboxMessagePublisher(
    private val db: JdbcClient,
    @Qualifier(MESSAGING_OBJECT_MAPPER) private val objectMapper: ObjectMapper,
    @Value("\${spring.application.name}") private val producer: String,
) : MessagePublisher {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun publish(
        aggregateType: String,
        aggregateId: String,
        event: DomainEvent,
    ) {
        val message = toMessage(event)
        if (message == null) {
            log.warn("통합 메시지 매핑 없음 - 발행 생략: {}", event::class.simpleName)
            return
        }

        val envelope =
            Envelope(
                eventId = UUID.randomUUID().toString(),
                // 컨슈머가 라우팅하는 기준은 계약(~Message) 클래스명이다. 도메인 이벤트명 아님.
                eventType = message::class.simpleName!!,
                eventVersion = 1,
                occurredAt = Instant.now(),
                aggregateType = aggregateType,
                aggregateId = aggregateId,
                // MDC가 비는 진입점(배치·스케줄러·컨슈머 스레드)에서도 다운스트림 체인을 묶을 뿌리를 만든다.
                // 진입점별 트레이스(배치 런 1회 = 트레이스 1개)는 배치 도입 시 MDC 시딩으로 — 여긴 최후 방어선.
                traceId = MDC.get("traceId") ?: UUID.randomUUID().toString(),
                producer = producer,
                payload = objectMapper.writeValueAsString(message),
            )
        db
            .sql(
                """
            INSERT INTO outbox_message (event_id, aggregate_type, aggregate_id, event_type, trace_id, payload)
            VALUES (UUID_TO_BIN(:eventId), :aggregateType, :aggregateId, :eventType, :traceId, :payload)
            """,
            ).param("eventId", envelope.eventId)
            .param("aggregateType", envelope.aggregateType)
            .param("aggregateId", envelope.aggregateId)
            .param("eventType", envelope.eventType)
            .param("traceId", envelope.traceId)
            .param("payload", objectMapper.writeValueAsString(envelope))  // 엔벨로프 전체가 payload 컬럼
            .update()
    }

    private fun toMessage(event: DomainEvent): IntegrationMessage? =
        when (event) {
            is IntegrationMessage -> event
            else -> null
        }
}
