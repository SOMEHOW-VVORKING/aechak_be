package com.aechak.api.eventbackbone

import com.aechak.api.support.KafkaIntegrationTestBase
import com.aechak.application.messaging.MessagePublisher
import com.aechak.application.messaging.ProcessedMessages
import com.aechak.domain.support.DomainEvent
import com.aechak.infra.kafka.Topics
import com.aechak.infra.kafka.config.MessagingJacksonConfig.Companion.MESSAGING_OBJECT_MAPPER
import com.aechak.message.Envelope
import com.aechak.message.IntegrationMessage
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.test.utils.KafkaTestUtils
import tools.jackson.databind.ObjectMapper
import java.time.Duration
import java.time.Instant
import java.util.UUID

class EventBackboneIntegrationTest : KafkaIntegrationTestBase() {
    @Autowired
    lateinit var publisher: MessagePublisher

    @Autowired
    lateinit var processedMessages: ProcessedMessages

    @Autowired
    lateinit var kafka: KafkaTemplate<String, String>

    @Autowired
    @Qualifier(MESSAGING_OBJECT_MAPPER)
    lateinit var objectMapper: ObjectMapper

    /** 테스트 전용 합성 이벤트. 도메인 이벤트이자 통합 메시지라 별도 매핑 없이 발행된다. */
    data class SyntheticMessage(
        val hello: String,
    ) : DomainEvent,
        IntegrationMessage

    @Test
    fun `발행하면 릴레이가 브로커까지 배달하고 PUBLISHED로 표시한다`() {
        val aggregateId = "publish-test"

        tx.executeWithoutResult {
            publisher.publish("order", aggregateId, SyntheticMessage("hi"))
        }

        // 릴레이는 0.5초 주기라 기다렸다 단언한다
        await().atMost(Duration.ofSeconds(15)).untilAsserted {
            assertThat(statusOf(aggregateId))
                .`as`("커밋된 아웃박스 행은 릴레이가 브로커 응답까지 받은 뒤 PUBLISHED(1)로 바뀌어야 한다")
                .isEqualTo(1)
        }
    }

    @Test
    fun `발행 트랜잭션이 롤백되면 아웃박스 행도 함께 사라진다`() {
        val aggregateId = "rollback-test"

        // 발행 후 도메인 로직 실패 → 전체 롤백
        runCatching {
            tx.executeWithoutResult {
                publisher.publish("order", aggregateId, SyntheticMessage("boom"))
                error("발행 이후 도메인 로직 실패 재현")
            }
        }

        val rows =
            db
                .sql("SELECT COUNT(*) FROM outbox_message WHERE aggregate_id = :aggregateId")
                .param("aggregateId", aggregateId)
                .query(Int::class.java)
                .single()
        assertThat(rows)
            .`as`("롤백된 트랜잭션의 아웃박스 행이 남아 있으면, 일어나지 않은 일의 이벤트가 발행된다")
            .isEqualTo(0)
    }

    @Test
    fun `같은 엔벨로프가 두 번 배달돼도 인박스에는 한 번만 기록된다`() {
        val eventId = UUID.randomUUID().toString()
        val aggregateId = "duplicate-test"
        val envelope = envelopeJson(eventId)

        kafka.send(Topics.ORDER, aggregateId, envelope).get()
        kafka.send(Topics.ORDER, aggregateId, envelope).get()

        await().atMost(Duration.ofSeconds(15)).untilAsserted {
            assertThat(inboxCount(eventId))
                .`as`("첫 배달은 인박스에 기록돼야 한다")
                .isEqualTo(1)
        }
        // "안 생김"은 기다려 봐야 안다
        Thread.sleep(1500)
        assertThat(inboxCount(eventId))
            .`as`("중복 배달이 두 번째 행을 만들면 컨슈머 효과가 두 번 실행된 것이다")
            .isEqualTo(1)
    }

    @Test
    fun `깨진 메시지는 dlt로 격리되고 다음 메시지는 정상 처리된다`() {
        kafka.send(Topics.ORDER, "poison", "THIS-IS-NOT-JSON{{{").get()
        val afterId = UUID.randomUUID().toString()
        kafka.send(Topics.ORDER, "after", envelopeJson(afterId)).get()

        // 순서 주의: poison이 파이프를 멈추지 않았음을 먼저 확인한다
        await().atMost(Duration.ofSeconds(30)).untilAsserted {
            assertThat(inboxCount(afterId))
                .`as`("깨진 메시지가 격리된 뒤에는 다음 메시지가 정상 처리돼야 한다")
                .isEqualTo(1)
        }

        // consumerProps 헬퍼는 4.0에서 지원 중단이라 직접 구성
        val props =
            mapOf<String, Any>(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to brokers,
                ConsumerConfig.GROUP_ID_CONFIG to "dlt-checker",
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
            )
        DefaultKafkaConsumerFactory<String, String>(props).createConsumer().use { consumer ->
            consumer.subscribe(listOf(Topics.ORDER_DLT))
            val records = KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(10))
            assertThat(records.records(Topics.ORDER_DLT))
                .`as`("재시도를 소진한 메시지는 원본 그대로 dlt 토픽에 격리돼야 한다")
                .anyMatch { it.value().contains("THIS-IS-NOT-JSON") }
        }
    }

    @Test
    fun `같은 애그리거트의 앞 행이 백오프 중이면 뒷 행은 발행되지 않고 다른 애그리거트는 흐른다`() {
        val blockedAggregate = "order-with-failure"
        val independentAggregate = "unrelated-order"

        insertOutboxRow(aggregateId = blockedAggregate, nextAttemptAtSql = "NOW(6) + INTERVAL 1 DAY") // 백오프 중인 앞 행
        insertOutboxRow(aggregateId = blockedAggregate) // 같은 애그리거트의 뒷 행
        insertOutboxRow(aggregateId = independentAggregate)

        await().atMost(Duration.ofSeconds(15)).untilAsserted {
            assertThat(statusOf(independentAggregate))
                .`as`("한 애그리거트의 실패가 무관한 애그리거트의 발행까지 막으면 안 된다")
                .isEqualTo(1)
        }
        val publishedCount =
            db
                .sql("SELECT COUNT(*) FROM outbox_message WHERE aggregate_id = :aggregateId AND status = 1")
                .param("aggregateId", blockedAggregate)
                .query(Int::class.java)
                .single()
        assertThat(publishedCount)
            .`as`("앞 행이 막힌 애그리거트에서 뒷 행이 먼저 발행되면 소비 순서가 깨진다")
            .isEqualTo(0)
    }

    @Test
    fun `전송 실패가 누적되면 9번째가 아니라 정확히 10번째에 DEAD가 된다`() {
        // 공백 든 토픽명은 전송이 반드시 실패한다 — mock 없이 실제 실패 경로(markFailed의 SQL)를 태우는 장치
        val aggregateId = "dead-transition-test"
        insertOutboxRow(aggregateType = "invalid topic!", aggregateId = aggregateId)
        // 8번 실패한 상태에서 시작해 9·10번째를 관찰한다
        db
            .sql("UPDATE outbox_message SET attempts = 8 WHERE aggregate_id = :aggregateId")
            .param("aggregateId", aggregateId)
            .update()

        // MySQL의 SET 순차 평가 탓에 9번째에 미리 죽던 버그의 회귀 방지
        await().atMost(Duration.ofSeconds(20)).untilAsserted {
            assertThat(attemptsOf(aggregateId))
                .`as`("아홉 번째 실패가 기록돼야 한다")
                .isEqualTo(9)
        }
        assertThat(statusOf(aggregateId))
            .`as`("아홉 번째 실패에서 DEAD가 되면 재시도 한 번을 도둑맞는 것이다")
            .isEqualTo(0)

        // 백오프를 건너뛰고 10번째 실패를 즉시 유도
        db
            .sql("UPDATE outbox_message SET next_attempt_at = NOW(6) WHERE aggregate_id = :aggregateId")
            .param("aggregateId", aggregateId)
            .update()
        await().atMost(Duration.ofSeconds(20)).untilAsserted {
            assertThat(statusOf(aggregateId))
                .`as`("열 번째 실패에서는 DEAD(2)로 격리돼 사람을 기다려야 한다")
                .isEqualTo(2)
        }
    }

    @Test
    fun `인박스는 컨슈머별로 중복을 판정한다`() {
        val eventId = UUID.randomUUID().toString()
        assertThat(processedMessages.markProcessed("consumer-a", eventId))
            .`as`("처음 보는 이벤트는 기록에 성공해야 한다")
            .isTrue()
        assertThat(processedMessages.markProcessed("consumer-a", eventId))
            .`as`("같은 컨슈머의 같은 이벤트는 중복으로 거절돼야 한다")
            .isFalse()
        assertThat(processedMessages.markProcessed("consumer-b", eventId))
            .`as`("다른 컨슈머는 같은 이벤트를 독립적으로 처리해야 한다")
            .isTrue()
    }

    /** 실제 발행 경로가 만드는 것과 같은 모양의 엔벨로프 JSON. */
    private fun envelopeJson(eventId: String): String =
        objectMapper.writeValueAsString(
            Envelope(
                eventId = eventId,
                eventType = "SyntheticMessage",
                eventVersion = 1,
                occurredAt = Instant.parse("2026-07-23T00:00:00Z"),
                aggregateType = "order",
                aggregateId = "test",
                traceId = "",
                producer = "test",
                payload = objectMapper.writeValueAsString(SyntheticMessage("from-test")),
            ),
        )

    /** 발행 경로를 거치지 않고 아웃박스 행을 직접 심는다. */
    private fun insertOutboxRow(
        aggregateType: String = "order",
        aggregateId: String,
        nextAttemptAtSql: String = "NOW(6)",
    ) {
        db
            .sql(
                """
                INSERT INTO outbox_message (event_id, aggregate_type, aggregate_id, event_type, trace_id, payload, next_attempt_at)
                VALUES (UUID_TO_BIN(:eventId), :aggregateType, :aggregateId, 'SyntheticMessage', '', '{"hello":"row"}', $nextAttemptAtSql)
                """.trimIndent(),
            ).param("eventId", UUID.randomUUID().toString())
            .param("aggregateType", aggregateType)
            .param("aggregateId", aggregateId)
            .update()
    }

    private fun statusOf(aggregateId: String): Int =
        db
            .sql("SELECT status FROM outbox_message WHERE aggregate_id = :aggregateId ORDER BY id LIMIT 1")
            .param("aggregateId", aggregateId)
            .query(Int::class.java)
            .single()

    private fun attemptsOf(aggregateId: String): Int =
        db
            .sql("SELECT attempts FROM outbox_message WHERE aggregate_id = :aggregateId ORDER BY id LIMIT 1")
            .param("aggregateId", aggregateId)
            .query(Int::class.java)
            .single()

    private fun inboxCount(eventId: String): Int =
        db
            .sql("SELECT COUNT(*) FROM processed_message WHERE event_id = UUID_TO_BIN(:eventId)")
            .param("eventId", eventId)
            .query(Int::class.java)
            .single()
}
