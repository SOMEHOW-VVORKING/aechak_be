package com.aechak.infra.kafka.outbox

import com.aechak.infra.kafka.publisher.KafkaSender
import org.apache.kafka.common.errors.InvalidTopicException
import org.apache.kafka.common.errors.RecordTooLargeException
import org.apache.kafka.common.errors.SerializationException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate
import java.sql.Timestamp
import java.time.Instant

/**
 * 다시 보내도 결과가 같은 실패만 분리. 이렇게 두지 않으면 유효 시간 동안 아웃박스 병목 발생.
 */
internal fun isPermanentFailure(e: Throwable): Boolean =
    generateSequence(e) { it.cause }.take(5).any {
        it is RecordTooLargeException ||
            it is InvalidTopicException ||
            it is SerializationException
    }

interface OutboxSweepTrigger {
    fun sweepNow()
}

@Component
internal class OutboxSweeper(
    private val db: JdbcClient,
    private val sender: KafkaSender,
    private val tx: TransactionTemplate,
    /**
     * 즉시 발행에 양보하는 유예(초). 이보다 젊은 행은 집지 않는다.
     *
     * 즉시 발행(`OutboxMessagePublisher.publishImmediately`)은 브로커 ack 를 받은 **뒤에**
     * 별 스레드에서 PUBLISHED 로 표시한다. 그 사이 행은 PENDING 이고, 스위퍼가 그때 틱하면
     * 같은 메시지를 한 번 더 보낸다. `FOR UPDATE SKIP LOCKED` 는 스위퍼끼리의 중복만 막고
     * 즉시 발행 경로와는 락을 공유하지 않아 이 경합에는 무효다.
     *
     * 스위퍼는 폴백이므로 빠른 경로와 경합해서는 안 된다. 기본 10초는 ack 와 UPDATE 가
     * 끝날 시간을 넉넉히 준 값이다. 대가는 즉시 발행이 **정말** 실패한 행의 재발행이
     * 유예만큼 늦어지는 것인데, 폴백의 성격상 받아들일 수 있다.
     */
    @Value("\${outbox.sweep-grace-seconds:10}") private val graceSeconds: Long,
) : OutboxSweepTrigger {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun sweepNow() {
        var more = true
        while (more) {
            more = tx.execute { sweepChunk() } ?: false
        }
    }

    /**
     * 잔량이 더 남았을 수 있고 실패도 없었을 때만 true.
     * 트랜잭션으로 감싸는 이유: FOR UPDATE 락이 트랜잭션 안에서만 유지되어 다중 인스턴스의 중복 처리를 방지
     */
    private fun sweepChunk(): Boolean {
        val rows =
            db
                .sql(
                    """
                SELECT o.id, o.event_id, o.aggregate_type,
                       o.ordering_key, o.event_type, o.trace_id, o.payload, o.expired_at
                FROM outbox_message o
                WHERE o.status = ${OutboxStatus.PENDING}
                  AND o.occurred_at < DATE_SUB(NOW(6), INTERVAL :graceSeconds SECOND)
                ORDER BY o.occurred_at, o.id
                LIMIT $CHUNK
                FOR UPDATE SKIP LOCKED
                """,
                ).param("graceSeconds", graceSeconds)
                .query { rs, _ ->
                    OutboxRow(
                        id = rs.getLong("id"),
                        eventId = rs.getString("event_id"),
                        aggregateType = rs.getString("aggregate_type"),
                        orderingKey = rs.getString("ordering_key"),
                        eventType = rs.getString("event_type"),
                        // 재발행은 같은 사건이라 traceId를 새로 만들지 않음(만들면 엔벨로프 본문과 갈라짐)
                        traceId = rs.getString("trace_id") ?: "",
                        payload = rs.getString("payload"),
                        expiredAt = rs.getTimestamp("expired_at"),
                    )
                }.list()

        // 만료 판정을 발행보다 먼저 끝냄. 아래에서 회차를 접어도 기한 지난 행은 이미 HOLD로 빠져 있음
        val now = Timestamp.from(Instant.now())
        val (expired, live) = rows.partition { it.expiredAt != null && !it.expiredAt.after(now) }
        expired.forEach { hold(it) }

        for (row in live) {
            try {
                send(row)
                db
                    .sql(
                        """
                    UPDATE outbox_message SET status = ${OutboxStatus.PUBLISHED}, published_at = :now
                    WHERE id = :id AND status = ${OutboxStatus.PENDING}
                    """,
                    ).param("now", Timestamp.from(Instant.now()))
                    .param("id", row.id)
                    .update()
            } catch (e: Exception) {
                if (isPermanentFailure(e)) {
                    markDead(row, e)
                    continue
                }
                // 만약 브로커가 아예 못 받는 상황이면 남은 행도 똑같이 실패함. 사이클을 끝내 락과 커넥션을 반납하고 남은 행은 다음 주기로 넘김
                log.warn("아웃박스 재발행 실패 (id={}, eventId={}, traceId={})", row.id, row.eventId, row.traceId, e)
                return false
            }
        }
        return rows.size == CHUNK
    }

    // TODO: 필요 시 취소 분기 추가 가능

    /** 다시 보내도 결과가 같은 행. 격리하지 않으면 정렬 선두에 남아 뒤 행 전체를 막음 */
    private fun markDead(
        row: OutboxRow,
        e: Exception,
    ) {
        db
            .sql("UPDATE outbox_message SET status = ${OutboxStatus.DEAD} WHERE id = :id AND status = ${OutboxStatus.PENDING}")
            .param("id", row.id)
            .update()
        log.error("발행 불가로 DEAD 격리, 수동 확인 대상 (id={}, eventId={}, traceId={})", row.id, row.eventId, row.traceId, e)
    }

    // TODO: 실패 시 취소하는 메세지 도입 시 등록된 eventType은 DEAD + 취소 실행으로 분기
    // TODO: 실패 알림 훅 연결
    private fun hold(row: OutboxRow) {
        db
            .sql("UPDATE outbox_message SET status = ${OutboxStatus.HOLD} WHERE id = :id AND status = ${OutboxStatus.PENDING}")
            .param("id", row.id)
            .update()
        log.warn("허용 지연 초과로 HOLD 전환, 수동 재개 대상 (id={}, eventId={}, traceId={})", row.id, row.eventId, row.traceId)
    }

    /**
     * 동기적으로 send.
     */
    private fun send(row: OutboxRow) {
        sender.send(row.aggregateType, row.orderingKey, row.eventId, row.eventType, row.traceId, row.payload).get()
    }

    companion object {
        private const val CHUNK = 50
    }

    private data class OutboxRow(
        val id: Long,
        val eventId: String,
        val aggregateType: String,
        val orderingKey: String,
        val eventType: String,
        val traceId: String,
        val payload: String,
        val expiredAt: Timestamp?,
    )
}
