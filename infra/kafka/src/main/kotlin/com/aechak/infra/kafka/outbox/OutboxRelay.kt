package com.aechak.infra.kafka.outbox

import com.aechak.infra.kafka.Topics
import com.aechak.infra.kafka.consumer.TraceIdRecordInterceptor
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate

/**
 * PENDING 상태인 아웃박스 레코드를 주기적으로 집어 Kafka로 전송
 * 같은 애그리거트의 더 앞선 미전송/실패 행이 있으면 새로 발행하지 않음 (==발행 순서 보장)
 * 전송 성공 시 PUBLISHED. 실패하면 지수 백오프 후 재시도, 횟수 초과 시 DEAD로 격리
 * 트랜잭션으로 감싸는 이유: FOR UPDATE 락이 트랜잭션 안에서만 유지되어 다중 인스턴스의 중복 처리를 방지하기 위함
 */
@Component
class OutboxRelay(
    private val db: JdbcClient,
    private val kafka: KafkaTemplate<String, String>,
    private val tx: TransactionTemplate,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelay = 500)
    fun poll() {
        tx.executeWithoutResult { drain() }
    }

    private fun drain() {
        val rows =
            db
                .sql(
                    """
                SELECT o.id, BIN_TO_UUID(o.event_id) AS event_id, o.aggregate_type,
                       o.aggregate_id, o.event_type, o.trace_id, o.payload, o.attempts
                FROM outbox_message o
                WHERE o.status = $PENDING AND o.next_attempt_at <= NOW(6)
                  AND NOT EXISTS (
                        SELECT 1 FROM outbox_message e
                        WHERE e.aggregate_type = o.aggregate_type
                          AND e.aggregate_id = o.aggregate_id
                          AND e.status IN ($PENDING, $DEAD) AND e.id < o.id)
                ORDER BY o.id
                LIMIT 200
                FOR UPDATE SKIP LOCKED
                """,
                ).query { rs, _ ->
                    OutboxRow(
                        id = rs.getLong("id"),
                        eventId = rs.getString("event_id"),
                        aggregateType = rs.getString("aggregate_type"),
                        aggregateId = rs.getString("aggregate_id"),
                        eventType = rs.getString("event_type"),
                        traceId = rs.getString("trace_id") ?: "",
                        payload = rs.getString("payload"),
                        attempts = rs.getInt("attempts"),
                    )
                }.list()

        for (row in rows) {
            try {
                send(row)
                db
                    .sql("UPDATE outbox_message SET status = $PUBLISHED, published_at = NOW(6) WHERE id = :id")
                    .param("id", row.id)
                    .update()
            } catch (e: Exception) {
                markFailed(row, e)
                // 만약 브로커가 아예 못 받는 상황이면 남은 행도 똑같이 실패함. 사이클을 끝내 락, 커넥션을 반납 후 다음 주기로 전달.
                break
            }
        }
    }

    /**
     * 동기적으로 send
     */
    private fun send(row: OutboxRow) {
        val record =
            ProducerRecord(Topics.of(row.aggregateType), row.aggregateId, row.payload)
        record.headers().add("event_id", row.eventId.toByteArray())
        record.headers().add("event_type", row.eventType.toByteArray())
        record.headers().add(TraceIdRecordInterceptor.TRACE_ID_HEADER, row.traceId.toByteArray())
        kafka.send(record).get()
    }

    /**
     * send 실패 시. status 상태 검증을 attempts 증가보다 먼저해야 함. (MySQL)
     */
    private fun markFailed(
        row: OutboxRow,
        e: Exception,
    ) {
        val backoffSeconds = 1L shl row.attempts.coerceIn(0, 9)
        db
            .sql(
                """
            UPDATE outbox_message
            SET status = IF(attempts + 1 >= :cap, $DEAD, status),
                attempts = attempts + 1,
                next_attempt_at = NOW(6) + INTERVAL :backoff SECOND
            WHERE id = :id
            """,
            ).param("backoff", backoffSeconds)
            .param("cap", MAX_ATTEMPTS)
            .param("id", row.id)
            .update()
        // TODO: DEAD 전환 시 알림 발송
        log.warn("아웃박스 전송 실패 (id={}, attempts={}): {}", row.id, row.attempts + 1, e.message)
    }

    private data class OutboxRow(
        val id: Long,
        val eventId: String,
        val aggregateType: String,
        val aggregateId: String,
        val eventType: String,
        val traceId: String,
        val payload: String,
        val attempts: Int,
    )

    companion object {
        // 재시도 최대 값.
        private const val MAX_ATTEMPTS = 10

        // outbox status 값.
        private const val PENDING = 0
        private const val PUBLISHED = 1
        private const val DEAD = 2
    }
}
