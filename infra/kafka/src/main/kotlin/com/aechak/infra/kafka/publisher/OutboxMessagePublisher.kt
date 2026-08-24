package com.aechak.infra.kafka.publisher

import com.aechak.infra.kafka.config.MessagingJacksonConfig.Companion.MESSAGING_OBJECT_MAPPER
import com.aechak.infra.kafka.outbox.OutboxStatus
import com.aechak.message.Envelope
import com.aechak.message.GuaranteedMessage
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.DisposableBean
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import tools.jackson.databind.ObjectMapper
import java.sql.Timestamp
import java.time.Instant
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.time.toJavaDuration

/** 중복은 생길 수 있고 유실은 없음 */
@Component
internal class OutboxMessagePublisher(
    private val db: JdbcClient,
    private val envelopes: EnvelopeFactory,
    private val sender: KafkaSender,
    @Qualifier(MESSAGING_OBJECT_MAPPER) private val objectMapper: ObjectMapper,
) : DisposableBean {
    private val log = LoggerFactory.getLogger(javaClass)

    // 공용 풀에서 DB를 기다리면 무관한 작업까지 같이 느려져서 전용 스레드로 뺌
    private val marker = Executors.newSingleThreadExecutor { r -> Thread(r, "outbox-marker").apply { isDaemon = true } }

    override fun destroy() {
        marker.shutdown()
        // 2초까지 기다려 보냈다는 기록을 마저 남김. 놓쳐도 미전송으로 남아 나중에 다시 보내짐
        marker.awaitTermination(2, TimeUnit.SECONDS)
    }

    fun publish(message: GuaranteedMessage) {
        check(TransactionSynchronizationManager.isActualTransactionActive()) {
            "유실 불가 메시지는 트랜잭션 안에서만 발행할 수 있다"
        }
        val allowedDelay = message.allowedDelay
        require(!allowedDelay.isNegative()) { "allowedDelay는 음수일 수 없다: $allowedDelay" }

        val envelope = envelopes.from(message)
        val payloadJson = objectMapper.writeValueAsString(envelope)
        val expiredAt =
            if (allowedDelay.isInfinite()) {
                null
            } else {
                envelope.occurredAt.plus(allowedDelay.toJavaDuration())
            }

        require(expiredAt == null || expiredAt.isAfter(Instant.now())) { "이미 만료된 메시지는 발행할 수 없다: expiredAt=$expiredAt" }

        db
            .sql(
                """
            INSERT INTO outbox_message (event_id, aggregate_type, ordering_key, event_type, trace_id, payload, occurred_at, expired_at)
            VALUES (:eventId, :aggregateType, :orderingKey, :eventType, :traceId, :payload, :occurredAt, :expiredAt)
            """,
            ).param("eventId", envelope.eventId)
            .param("aggregateType", envelope.aggregateType)
            .param("orderingKey", envelope.orderingKey)
            .param("eventType", envelope.eventType)
            .param("traceId", envelope.traceId)
            .param("payload", payloadJson)  // 엔벨로프 전체가 payload 컬럼
            .param("occurredAt", Timestamp.from(envelope.occurredAt))
            .param("expiredAt", expiredAt?.let { Timestamp.from(it) })
            .update()

        TransactionSynchronizationManager.registerSynchronization(
            object : TransactionSynchronization {
                override fun afterCommit() = publishImmediately(envelope, payloadJson)
            },
        )
    }

    private fun publishImmediately(
        envelope: Envelope,
        payloadJson: String,
    ) {
        try {
            sender
                .send(
                    envelope.aggregateType,
                    envelope.orderingKey,
                    envelope.eventId,
                    envelope.eventType,
                    envelope.traceId,
                    payloadJson,
                ).whenCompleteAsync({ _, e ->
                    if (e == null) {
                        markPublished(envelope.eventId)
                    } else {
                        log.warn(
                            "즉시 발행 실패 - PENDING 유지, 배치가 재발행 (eventId={}, traceId={})",
                            envelope.eventId,
                            envelope.traceId,
                            e,
                        )
                    }
                }, marker)
        } catch (e: Exception) {
            log.warn("즉시 발행 실패 - PENDING 유지, 배치가 재발행 (eventId={}, traceId={})", envelope.eventId, envelope.traceId, e)
        }
    }

    private fun markPublished(eventId: String) {
        try {
            db
                .sql(
                    """
                UPDATE outbox_message SET status = ${OutboxStatus.PUBLISHED}, published_at = :now
                WHERE event_id = :eventId AND status = ${OutboxStatus.PENDING}
                """,
                ).param("now", Timestamp.from(Instant.now()))
                .param("eventId", eventId)
                .update()
        } catch (e: Exception) {
            // 중복은 받는 쪽이 거름
            log.warn("PUBLISHED 마킹 실패 (eventId={})", eventId, e)
        }
    }
}
