package com.aechak.api.eventbackbone

import com.aechak.api.support.KafkaIntegrationTestBase
import com.aechak.application.messaging.MessagePublisher
import com.aechak.application.messaging.ProcessedMessages
import com.aechak.infra.kafka.Topics
import com.aechak.infra.kafka.config.MessagingJacksonConfig.Companion.MESSAGING_OBJECT_MAPPER
import com.aechak.infra.kafka.consumer.TraceIdRecordInterceptor
import com.aechak.infra.kafka.outbox.OutboxSweepTrigger
import com.aechak.message.BestEffortMessage
import com.aechak.message.Envelope
import com.aechak.message.GuaranteedMessage
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringDeserializer
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Test
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.core.env.Environment
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.config.KafkaListenerEndpointRegistry
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.test.utils.ContainerTestUtils
import org.springframework.kafka.test.utils.KafkaTestUtils
import org.springframework.transaction.support.TransactionTemplate
import tools.jackson.databind.ObjectMapper
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.LinkedBlockingQueue
import kotlin.time.Duration.Companion.minutes

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

    @Autowired
    lateinit var sweeper: OutboxSweepTrigger

    @Autowired
    lateinit var traceProbe: TraceProbe

    @Autowired
    lateinit var registry: KafkaListenerEndpointRegistry

    @Autowired
    lateinit var environment: Environment

    /** 멱등키·시각을 생성자 프로퍼티로 둬 copy·재읽기에도 안 바뀜 */
    data class SyntheticMessage(
        val hello: String,
        override val orderingKey: String = "test",
        override val eventId: String = UUID.randomUUID().toString(),
        override val occurredAt: Instant = Instant.now(),
    ) : GuaranteedMessage {
        override val aggregateType: String = "order"
    }

    data class SyntheticExpiringMessage(
        val hello: String,
        override val orderingKey: String,
        override val occurredAt: Instant = Instant.now(),
        override val eventId: String = UUID.randomUUID().toString(),
    ) : GuaranteedMessage {
        override val aggregateType: String = "order"
        override val allowedDelay: kotlin.time.Duration = 30.minutes
    }

    data class SyntheticNegativeDelayMessage(
        override val orderingKey: String,
        override val eventId: String = UUID.randomUUID().toString(),
        override val occurredAt: Instant = Instant.now(),
    ) : GuaranteedMessage {
        override val aggregateType: String = "order"
        override val allowedDelay: kotlin.time.Duration = (-1).minutes
    }

    data class SyntheticDualMarkerMessage(
        override val orderingKey: String,
        override val eventId: String = UUID.randomUUID().toString(),
        override val occurredAt: Instant = Instant.now(),
    ) : GuaranteedMessage,
        BestEffortMessage {
        override val aggregateType: String = "order"
    }

    /** 공백 든 토픽명은 전송이 반드시 실패함 */
    data class SyntheticUnsendableMessage(
        override val orderingKey: String,
        override val eventId: String = UUID.randomUUID().toString(),
        override val occurredAt: Instant = Instant.now(),
    ) : BestEffortMessage {
        override val aggregateType: String = "invalid topic!"
    }

    data class SyntheticUnsendableGuaranteedMessage(
        override val orderingKey: String,
        override val eventId: String = UUID.randomUUID().toString(),
        override val occurredAt: Instant = Instant.now(),
    ) : GuaranteedMessage {
        override val aggregateType: String = "invalid topic!"
    }

    data class SyntheticBestEffortMessage(
        val hello: String,
        override val orderingKey: String,
        override val eventId: String = UUID.randomUUID().toString(),
        override val occurredAt: Instant = Instant.now(),
    ) : BestEffortMessage {
        override val aggregateType: String = "order"
    }

    @Test
    fun `발행하면 브로커까지 배달되고 PUBLISHED로 표시된다`() {
        val orderingKey = "publish-test"

        tx.executeWithoutResult {
            publisher.publish(SyntheticMessage("hi", orderingKey))
        }

        // 스위퍼 스케줄은 batch 소속이라 이 컨텍스트엔 없음
        await().atMost(Duration.ofSeconds(15)).untilAsserted {
            assertThat(statusOf(orderingKey))
                .`as`("커밋된 아웃박스 행은 브로커 응답까지 받은 뒤 PUBLISHED(1)로 바뀌어야 한다")
                .isEqualTo(1)
        }
        val publishedAtSet =
            db
                .sql("SELECT published_at IS NOT NULL FROM outbox_message WHERE ordering_key = :orderingKey")
                .param("orderingKey", orderingKey)
                .query(Boolean::class.java)
                .single()
        assertThat(publishedAtSet)
            .`as`("published_at은 보존·청소(14일)의 기준이다 — NULL이면 그 행은 영원히 안 지워진다")
            .isTrue()
        assertThat(traceIdOf(orderingKey))
            .`as`("MDC 없는 발행 경로에서도 체인 뿌리가 될 traceId가 만들어져야 한다")
            .isNotBlank()
    }

    @Test
    fun `즉시 발행은 스위퍼 개입 없이 PUBLISHED로 만든다`() {
        val orderingKey = "immediate-success-test"
        // 스위퍼 스케줄은 batch 소속이라 이 컨텍스트에 없음. PUBLISHED 전환은 즉시 발행만 만들 수 있는 결과
        val message = SyntheticMessage("fast", orderingKey)
        tx.executeWithoutResult {
            publisher.publish(message)
        }

        await().atMost(Duration.ofSeconds(15)).untilAsserted {
            assertThat(statusOfEvent(message.eventId))
                .`as`("afterCommit 즉시 발행이 사라지는 회귀가 나면 이 행은 아무도 발행하지 않아 영원히 PENDING이다")
                .isEqualTo(1)
        }
    }

    @Test
    fun `발행 스레드의 MDC traceId가 이벤트에 그대로 실린다`() {
        val orderingKey = "mdc-propagation-test"
        // TraceIdFilter가 HTTP 스레드에 넣어주는 것과 같은 상황을 리터럴 키로 재현
        MDC.put("traceId", "trace-from-request")
        try {
            tx.executeWithoutResult {
                publisher.publish(SyntheticMessage("hi", orderingKey))
            }
        } finally {
            MDC.remove("traceId")
        }

        assertThat(traceIdOf(orderingKey))
            .`as`("퍼블리셔가 다른 MDC 키를 읽으면 요청과 이벤트의 트레이스가 끊긴다")
            .isEqualTo("trace-from-request")
    }

    @Test
    fun `로그 패턴은 MDC의 traceId를 출력한다`() {
        assertThat(environment.getProperty("logging.pattern.level"))
            .`as`("패턴에서 %X{traceId}가 빠지면 traceId가 어디에도 찍히지 않는다")
            .contains("%X{traceId")
    }

    @Test
    fun `기본 허용 지연은 만료 없음으로 기록한다`() {
        val orderingKey = "no-expiry-test"
        tx.executeWithoutResult {
            publisher.publish(SyntheticMessage("hi", orderingKey))
        }

        val expiredAtNull =
            db
                .sql("SELECT expired_at IS NULL FROM outbox_message WHERE ordering_key = :orderingKey")
                .param("orderingKey", orderingKey)
                .query(Boolean::class.java)
                .single()
        assertThat(expiredAtNull)
            .`as`("INFINITE 허용 지연은 만료가 없어야 한다(NULL) — 값이 생기면 스위퍼가 언젠가 죽인다")
            .isTrue()
    }

    @Test
    fun `유한 허용 지연은 만료 시각을 사건 시각 더하기 지연으로 기록한다`() {
        val orderingKey = "expiry-test"
        val occurredAt = Instant.now().minusSeconds(600) // 과거 사건. 기준이 INSERT 시각이 아님을 같이 고정
        tx.executeWithoutResult {
            publisher.publish(SyntheticExpiringMessage("hi", orderingKey, occurredAt))
        }

        val occurredDiffSeconds =
            db
                .sql("SELECT ABS(TIMESTAMPDIFF(SECOND, occurred_at, :occurredAt)) FROM outbox_message WHERE ordering_key = :orderingKey")
                .param("occurredAt", java.sql.Timestamp.from(occurredAt))
                .param("orderingKey", orderingKey)
                .query(Long::class.java)
                .single()
        assertThat(occurredDiffSeconds)
            .`as`("occurred_at은 INSERT 시각이 아니라 메시지의 사건 시각이어야 한다")
            .isZero()

        val delaySeconds =
            db
                .sql("SELECT TIMESTAMPDIFF(SECOND, occurred_at, expired_at) FROM outbox_message WHERE ordering_key = :orderingKey")
                .param("orderingKey", orderingKey)
                .query(Long::class.java)
                .single()
        assertThat(delaySeconds)
            .`as`("expired_at은 정확히 occurred_at + allowedDelay(30분)여야 한다")
            .isEqualTo(1800L)
    }

    @Test
    fun `엔벨로프 와이어 형태를 고정한다`() {
        val orderingKey = "golden-shape-test"
        tx.executeWithoutResult {
            publisher.publish(SyntheticExpiringMessage("shape", orderingKey))
        }

        val payloadJson =
            db
                .sql("SELECT payload FROM outbox_message WHERE ordering_key = :orderingKey")
                .param("orderingKey", orderingKey)
                .query(String::class.java)
                .single()

        @Suppress("UNCHECKED_CAST")
        val envelope = objectMapper.readValue(payloadJson, Map::class.java) as Map<String, Any?>
        assertThat(envelope.keys)
            .`as`("엔벨로프 필드 구성은 컨슈머와의 와이어 계약이다 — 여기가 깨지면 의도한 스키마 변경인지 확인할 것")
            .containsExactlyInAnyOrder(
                "eventId",
                "eventType",
                "occurredAt",
                "aggregateType",
                "orderingKey",
                "traceId",
                "producer",
                "payload",
            )

        @Suppress("UNCHECKED_CAST")
        val inner = objectMapper.readValue(envelope["payload"] as String, Map::class.java) as Map<String, Any?>
        assertThat(inner)
            .`as`("발행 정책 노브(allowedDelay)는 공개 와이어로 새면 안 된다")
            .doesNotContainKey("allowedDelay")
        assertThat(inner["hello"]).isEqualTo("shape")
    }

    @Test
    fun `태생 만료 메시지는 시끄럽게 거부된다`() {
        assertThatThrownBy {
            tx.executeWithoutResult {
                publisher.publish(SyntheticExpiringMessage("stale", "born-expired-test", Instant.now().minusSeconds(3600)))
            }
        }.`as`("이미 만료된 채 저장되면 즉시 발행은 내보내는데 스위퍼는 HOLD로 잡아 상태가 어긋난다")
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `음수 허용 지연은 시끄럽게 거부된다`() {
        assertThatThrownBy {
            tx.executeWithoutResult {
                publisher.publish(SyntheticNegativeDelayMessage("negative-delay-test"))
            }
        }.`as`("음수 지연이 조용히 저장되면 만료 처리 도입 순간 발행 기회 없이 즉사한다")
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `두 마커를 동시에 구현하면 유실 불가로 처리된다`() {
        val orderingKey = "dual-marker-test"
        tx.executeWithoutResult {
            publisher.publish(SyntheticDualMarkerMessage(orderingKey))
        }

        val outboxRows =
            db
                .sql("SELECT COUNT(*) FROM outbox_message WHERE ordering_key = :orderingKey")
                .param("orderingKey", orderingKey)
                .query(Int::class.java)
                .single()
        assertThat(outboxRows)
            .`as`("잘못된 이중 선언은 안전한 쪽(아웃박스)으로 떨어져야 한다. when 분기 순서의 고정")
            .isEqualTo(1)
    }

    @Test
    fun `유실 가능 메시지는 브로커에 못 보내도 호출부로 예외를 전파하지 않는다`() {
        assertThatCode { publisher.publish(SyntheticUnsendableMessage("no-broker-test")) }
            .`as`("유실 가능 메시지의 발행 실패가 호출부를 깨면 계약(로그만 남기고 버림) 위반이다")
            .doesNotThrowAnyException()
    }

    @Test
    fun `즉시 발행이 실패해도 커밋은 성공하고 행은 PENDING으로 남는다`() {
        val orderingKey = "immediate-fail-test"
        assertThatCode {
            tx.executeWithoutResult {
                publisher.publish(SyntheticUnsendableGuaranteedMessage(orderingKey))
            }
        }.`as`("커밋 후 즉시 발행의 실패가 호출부로 새면 이미 커밋된 요청이 실패로 둔갑한다")
            .doesNotThrowAnyException()

        Thread.sleep(1500)
        assertThat(statusOf(orderingKey))
            .`as`("즉시 발행이 실패한 행은 PENDING(0)으로 남아 배치 재발행 대상이 돼야 한다")
            .isEqualTo(0)
    }

    @Test
    fun `멱등키는 대소문자를 구분한다`() {
        val base = "Case-${UUID.randomUUID()}"
        assertThat(processedMessages.markProcessed("case-consumer", base))
            .`as`("첫 기록은 성공해야 한다")
            .isTrue()
        assertThat(processedMessages.markProcessed("case-consumer", base.lowercase()))
            .`as`("대소문자만 다른 키가 중복으로 판정되면 다른 사건이 헛스킵된다 (utf8mb4_bin 회귀 감지)")
            .isTrue()
    }

    @Test
    fun `유실 불가 메시지는 트랜잭션 밖 발행을 거부한다`() {
        assertThatThrownBy { publisher.publish(SyntheticMessage("no-tx", "outside-tx-test")) }
            .`as`("트랜잭션 밖 INSERT는 자동커밋되어 원자성 계약이 조용히 깨지므로 시끄럽게 거부해야 한다")
            .isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun `유실 가능 메시지는 아웃박스를 거치지 않고 커밋 후 브로커에 도착한다`() {
        val traceId = "direct-commit-${UUID.randomUUID()}"
        val orderingKey = "direct-tx-test"
        MDC.put("traceId", traceId)
        try {
            tx.executeWithoutResult {
                publisher.publish(SyntheticBestEffortMessage("direct", orderingKey))
            }
        } finally {
            MDC.remove("traceId")
        }

        await().atMost(Duration.ofSeconds(15)).untilAsserted {
            assertThat(traceProbe.captured)
                .`as`("커밋되면 폴링 주기를 기다리지 않고 바로 도착해야 한다")
                .contains(traceId)
        }
        val outboxRows =
            db
                .sql("SELECT COUNT(*) FROM outbox_message WHERE ordering_key = :orderingKey")
                .param("orderingKey", orderingKey)
                .query(Int::class.java)
                .single()
        assertThat(outboxRows)
            .`as`("유실 가능 메시지가 아웃박스에 행을 남기면 스위퍼가 같은 이벤트를 한 번 더 발행한다")
            .isEqualTo(0)
    }

    @Test
    fun `유실 가능 메시지도 트랜잭션이 롤백되면 발행하지 않는다`() {
        // 할당 전에는 관찰 자체가 꺼져 있어서 부재 단언이 무조건 통과함
        ContainerTestUtils.waitForAssignment(registry.getListenerContainer(TraceProbe.ID)!!, 1)

        val traceId = "direct-rollback-${UUID.randomUUID()}"
        MDC.put("traceId", traceId)
        try {
            runCatching {
                tx.executeWithoutResult {
                    publisher.publish(SyntheticBestEffortMessage("boom", "direct-rollback-test"))
                    error("발행 이후 도메인 로직 실패 재현")
                }
            }
        } finally {
            MDC.remove("traceId")
        }

        // 한 시점에 없는 것과 계속 없는 것은 다름. 고정 대기로는 늦게 온 레코드가 통과함
        await()
            .during(Duration.ofSeconds(2))
            .atMost(Duration.ofSeconds(10))
            .untilAsserted {
                assertThat(traceProbe.captured)
                    .`as`("롤백된 트랜잭션의 이벤트가 나가면 일어나지 않은 일을 다른 컨슈머가 실행한다")
                    .doesNotContain(traceId)
            }
    }

    @Test
    fun `유실 가능 메시지는 트랜잭션 밖에서는 즉시 발행한다`() {
        val traceId = "direct-no-tx-${UUID.randomUUID()}"
        MDC.put("traceId", traceId)
        try {
            publisher.publish(SyntheticBestEffortMessage("now", "direct-no-tx-test"))
        } finally {
            MDC.remove("traceId")
        }

        await().atMost(Duration.ofSeconds(15)).untilAsserted {
            assertThat(traceProbe.captured)
                .`as`("트랜잭션이 없으면 등록할 커밋 시점이 없으므로 그 자리에서 발행돼야 한다")
                .contains(traceId)
        }
    }

    @Test
    fun `컨슈머 스레드는 레코드 헤더의 traceId를 MDC로 복원받는다`() {
        val traceId = "trace-chain-${UUID.randomUUID()}"
        val record = ProducerRecord(Topics.ORDER, null, "trace-test", envelopeJson(UUID.randomUUID().toString()))
        record.headers().add(TraceIdRecordInterceptor.TRACE_ID_HEADER, traceId.toByteArray())

        kafka.send(record).get()

        await().atMost(Duration.ofSeconds(15)).untilAsserted {
            assertThat(traceProbe.captured)
                .`as`("복원이 안 되면 컨슈머 로그·재발행 이벤트가 발행 홉의 traceId와 끊긴다")
                .contains(traceId)
        }
    }

    @Test
    fun `발행 트랜잭션이 롤백되면 아웃박스 행도 사라지고 브로커로도 나가지 않는다`() {
        val orderingKey = "rollback-test"
        val traceId = "outbox-rollback-${UUID.randomUUID()}"

        // 발행 후 도메인 로직 실패 → 전체 롤백
        MDC.put("traceId", traceId)
        try {
            runCatching {
                tx.executeWithoutResult {
                    publisher.publish(SyntheticMessage("boom", orderingKey))
                    error("발행 이후 도메인 로직 실패 재현")
                }
            }
        } finally {
            MDC.remove("traceId")
        }

        val rows =
            db
                .sql("SELECT COUNT(*) FROM outbox_message WHERE ordering_key = :orderingKey")
                .param("orderingKey", orderingKey)
                .query(Int::class.java)
                .single()
        assertThat(rows)
            .`as`("롤백된 트랜잭션의 아웃박스 행이 남아 있으면, 일어나지 않은 일의 이벤트가 발행된다")
            .isEqualTo(0)

        // "안 나감"은 기다려 봐야 앎. afterCompletion 회귀는 행 카운트로 안 잡힘
        Thread.sleep(1500)
        assertThat(traceProbe.captured)
            .`as`("롤백됐는데 즉시 발행이 나가면 일어나지 않은 일을 다른 컨슈머가 실행한다 (유령)")
            .doesNotContain(traceId)
    }

    @Test
    fun `같은 엔벨로프가 두 번 배달돼도 인박스에는 한 번만 기록된다`() {
        val eventId = UUID.randomUUID().toString()
        val orderingKey = "duplicate-test"
        val envelope = envelopeJson(eventId)

        kafka.send(Topics.ORDER, orderingKey, envelope).get()
        kafka.send(Topics.ORDER, orderingKey, envelope).get()

        await().atMost(Duration.ofSeconds(15)).untilAsserted {
            assertThat(inboxCount(eventId))
                .`as`("첫 배달은 인박스에 기록돼야 한다")
                .isEqualTo(1)
        }
        // "안 생김"은 기다려 봐야 앎
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

        // 순서 주의: poison이 파이프를 멈추지 않았음을 먼저 확인함
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
    fun `처리가 실패해 롤백되면 인박스 기록도 사라져 재시도가 막히지 않는다`() {
        val eventId = UUID.randomUUID().toString()

        runCatching {
            tx.executeWithoutResult {
                processedMessages.markProcessed("tx-consumer", eventId)
                error("인박스 기록 후 처리 실패 재현")
            }
        }

        assertThat(inboxCount(eventId))
            .`as`("실패한 처리가 '처리됨'으로 남으면 재전달이 헛스킵되어 메시지가 유실된다")
            .isEqualTo(0)
        assertThat(processedMessages.markProcessed("tx-consumer", eventId))
            .`as`("롤백 후 재전달은 새 처리로 받아들여져야 한다")
            .isTrue()
    }

    @Test
    fun `발행 완료 기록 전에 죽어 같은 행이 두 번 발행돼도 컨슈머 효과는 한 번이다`() {
        val eventId = UUID.randomUUID().toString()
        val orderingKey = "redelivery-test"
        insertOutboxRow(orderingKey = orderingKey, eventId = eventId)
        sweeper.sweepNow()

        await().atMost(Duration.ofSeconds(15)).untilAsserted {
            assertThat(inboxCount(eventId))
                .`as`("첫 발행분이 처리돼야 한다")
                .isEqualTo(1)
        }

        // 브로커 전송 후 PUBLISHED 기록 직전에 스위퍼가 죽은 상황 재현: 행이 PENDING으로 남음
        db
            .sql("UPDATE outbox_message SET status = 0 WHERE ordering_key = :orderingKey")
            .param("orderingKey", orderingKey)
            .update()
        sweeper.sweepNow()

        assertThat(statusOf(orderingKey))
            .`as`("스위퍼는 PENDING으로 남은 행을 다시 발행해야 한다")
            .isEqualTo(1)
        Thread.sleep(1500)
        assertThat(inboxCount(eventId))
            .`as`("중복 발행이 효과를 두 번 내면 at-least-once가 exactly-once 효과로 좁혀지지 않는 것이다")
            .isEqualTo(1)
    }

    @Test
    fun `다시 보내도 실패할 행은 DEAD로 격리하고 뒷 행은 계속 발행한다`() {
        val deadAggregate = "sweep-dead-row"
        val laterAggregate = "sweep-later-row"
        insertOutboxRow(aggregateType = "invalid topic!", orderingKey = deadAggregate) // occurred_at이 앞선 영구 실패 행
        insertOutboxRow(orderingKey = laterAggregate)

        sweeper.sweepNow()

        assertThat(statusOf(deadAggregate))
            .`as`("영구 실패 행이 선두에 남으면 매 주기 같은 자리에서 멈춰 뒤 행 전체가 영원히 안 나간다")
            .isEqualTo(2)
        assertThat(statusOf(laterAggregate))
            .`as`("격리한 뒤에는 뒷 행이 정상 발행돼야 한다")
            .isEqualTo(1)
    }

    @Test
    fun `스위퍼는 청크 크기를 넘는 잔량도 한 번의 구동으로 모두 재발행한다`() {
        repeat(60) { insertOutboxRow(orderingKey = "bulk-$it") }

        sweeper.sweepNow()

        val pending =
            db
                .sql("SELECT COUNT(*) FROM outbox_message WHERE status = 0")
                .query(Int::class.java)
                .single()
        assertThat(pending)
            .`as`("소분 루프가 첫 청크(50)에서 멈추면 잔량이 다음 스케줄 주기까지 방치된다")
            .isEqualTo(0)
    }

    @Test
    fun `스위퍼는 종결된 행을 다시 집지 않는다`() {
        val publishedEventId = UUID.randomUUID().toString()
        val deadEventId = UUID.randomUUID().toString()
        val holdEventId = UUID.randomUUID().toString()
        insertOutboxRow(orderingKey = "skip-published", eventId = publishedEventId)
        insertOutboxRow(orderingKey = "skip-dead", eventId = deadEventId)
        insertOutboxRow(orderingKey = "skip-hold", eventId = holdEventId)
        db.sql("UPDATE outbox_message SET status = 1 WHERE ordering_key = 'skip-published'").update()
        db.sql("UPDATE outbox_message SET status = 2 WHERE ordering_key = 'skip-dead'").update()
        db.sql("UPDATE outbox_message SET status = 3 WHERE ordering_key = 'skip-hold'").update()

        sweeper.sweepNow()

        // "안 나감"은 기다려 봐야 앎
        Thread.sleep(1500)
        assertThat(inboxCount(publishedEventId))
            .`as`("PUBLISHED 행을 다시 집으면 매 주기 전량 재발행이 무한 반복된다")
            .isEqualTo(0)
        assertThat(inboxCount(deadEventId))
            .`as`("DEAD는 종결 상태다. 다시 발행되면 취소가 실행된 사건이 브로커에 나간다")
            .isEqualTo(0)
        assertThat(inboxCount(holdEventId))
            .`as`("HOLD는 사람 대기 상태다. 재개 전에 나가면 안 된다")
            .isEqualTo(0)
    }

    @Test
    fun `만료된 행은 발행하지 않고 HOLD로 전환한다`() {
        val eventId = UUID.randomUUID().toString()
        val orderingKey = "expired-hold-test"
        insertOutboxRow(orderingKey = orderingKey, eventId = eventId, expiredAt = Instant.now().minusSeconds(3600))

        sweeper.sweepNow()

        assertThat(statusOf(orderingKey))
            .`as`("기한 지난 행을 계속 재발행하면 허용 지연 계약이 거짓이 된다")
            .isEqualTo(3)
        // "안 나감"은 기다려 봐야 앎
        Thread.sleep(1500)
        assertThat(inboxCount(eventId))
            .`as`("HOLD로 전환된 행이 브로커로 나가면 안 된다")
            .isEqualTo(0)
    }

    @Test
    fun `만료 행은 사이클을 접지 않고 미만료 유한 기한 행은 정상 재발행된다`() {
        val expiredEventId = UUID.randomUUID().toString()
        val liveEventId = UUID.randomUUID().toString()
        insertOutboxRow(orderingKey = "expiry-mixed-expired", eventId = expiredEventId, expiredAt = Instant.now().minusSeconds(3600))
        insertOutboxRow(orderingKey = "expiry-mixed-live", eventId = liveEventId, expiredAt = Instant.now().plusSeconds(3600))

        sweeper.sweepNow()

        assertThat(statusOf("expiry-mixed-expired"))
            .`as`("앞선 만료 행은 HOLD로 빠져야 한다")
            .isEqualTo(3)
        assertThat(statusOf("expiry-mixed-live"))
            .`as`("만료는 전송 실패가 아니라 사이클을 접지 않고, 기한 전 행은 재발행돼야 한다")
            .isEqualTo(1)
    }

    @Test
    fun `HOLD를 수동으로 재개하면 다시 발행된다`() {
        val eventId = UUID.randomUUID().toString()
        val orderingKey = "hold-resume-test"
        insertOutboxRow(orderingKey = orderingKey, eventId = eventId, expiredAt = Instant.now().minusSeconds(3600))
        sweeper.sweepNow()
        assertThat(statusOf(orderingKey))
            .`as`("재개 테스트의 전제: 먼저 HOLD가 돼야 한다")
            .isEqualTo(3)

        // 운영자 재개 재현: 기한을 풀고 PENDING 복귀. 기한을 안 풀면 다음 사이클에 도로 HOLD
        db
            .sql("UPDATE outbox_message SET status = 0, expired_at = NULL WHERE ordering_key = :orderingKey")
            .param("orderingKey", orderingKey)
            .update()
        sweeper.sweepNow()

        assertThat(statusOf(orderingKey))
            .`as`("재개된 행은 다음 구동에서 발행돼야 한다")
            .isEqualTo(1)
        await().atMost(Duration.ofSeconds(15)).untilAsserted {
            assertThat(inboxCount(eventId))
                .`as`("재개 발행분이 소비돼야 한다")
                .isEqualTo(1)
        }
    }

    @Test
    fun `엔벨로프에 모르는 필드가 있어도 소비는 실패하지 않는다`() {
        val eventId = UUID.randomUUID().toString()
        val withUnknownField = envelopeJson(eventId).removeSuffix("}") + ""","addedInV2":"future"}"""

        kafka.send(Topics.ORDER, "tolerant-reader-test", withUnknownField).get()

        await().atMost(Duration.ofSeconds(15)).untilAsserted {
            assertThat(inboxCount(eventId))
                .`as`("프로듀서가 필드를 먼저 추가해도 배포가 늦은 컨슈머는 계속 소비할 수 있어야 한다")
                .isEqualTo(1)
        }
    }

    @Test
    fun `브로커 레코드의 헤더 3종은 엔벨로프와 같은 값으로 실린다`() {
        val orderingKey = "header-contract-test"
        val traceId = "header-trace-${UUID.randomUUID()}"
        val message = SyntheticMessage("hdr", orderingKey)
        MDC.put("traceId", traceId)
        try {
            tx.executeWithoutResult {
                publisher.publish(message)
            }
        } finally {
            MDC.remove("traceId")
        }

        val props =
            mapOf<String, Any>(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to brokers,
                ConsumerConfig.GROUP_ID_CONFIG to "header-checker",
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
            )
        DefaultKafkaConsumerFactory<String, String>(props).createConsumer().use { consumer ->
            consumer.subscribe(listOf(Topics.ORDER))
            val matched = mutableListOf<ConsumerRecord<String, String>>()
            val deadline = System.currentTimeMillis() + 15_000
            while (matched.isEmpty() && System.currentTimeMillis() < deadline) {
                consumer.poll(Duration.ofMillis(500)).records(Topics.ORDER).forEach {
                    if (it.key() == orderingKey) matched.add(it)
                }
            }
            assertThat(matched).`as`("발행된 레코드가 브로커에 도착해야 한다").isNotEmpty()

            // send는 String 6개 위치 인자라 스왑돼도 컴파일이 못 잡음
            matched.forEach { record ->
                fun header(name: String) =
                    record
                        .headers()
                        .lastHeader(name)
                        ?.value()
                        ?.decodeToString()
                assertThat(header("event_id"))
                    .`as`("event_id 헤더가 멱등키와 다르면 헤더 기반 dedup 도입 순간 터진다")
                    .isEqualTo(message.eventId)
                assertThat(header("event_type"))
                    .`as`("event_type 헤더는 컨슈머 라우팅 기준이다")
                    .isEqualTo("SyntheticMessage")
                assertThat(header(TraceIdRecordInterceptor.TRACE_ID_HEADER))
                    .`as`("trace_id 헤더가 끊기면 이벤트 체인 추적이 발행 홉에서 끊긴다")
                    .isEqualTo(traceId)
            }
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
                occurredAt = Instant.parse("2026-07-23T00:00:00Z"),
                aggregateType = "order",
                orderingKey = "test",
                traceId = "",
                producer = "test",
                payload = objectMapper.writeValueAsString(SyntheticMessage("from-test")),
            ),
        )

    /**
     * 발행 경로를 거치지 않고 아웃박스 행을 직접 심음. payload는 실제 발행과 같은 엔벨로프 JSON.
     * 만료 시딩은 JVM 시계로 받음. DB NOW와 혼용하면 컨테이너(UTC)와 JVM(KST)의 타임존 스큐로 비교가 뒤틀림
     */
    private fun insertOutboxRow(
        aggregateType: String = "order",
        orderingKey: String,
        eventId: String = UUID.randomUUID().toString(),
        expiredAt: Instant? = null,
    ) {
        db
            .sql(
                """
                INSERT INTO outbox_message (event_id, aggregate_type, ordering_key, event_type, trace_id, payload, occurred_at, expired_at)
                VALUES (:eventId, :aggregateType, :orderingKey, 'SyntheticMessage', '', :payload, NOW(6), :expiredAt)
                """.trimIndent(),
            ).param("expiredAt", expiredAt?.let { java.sql.Timestamp.from(it) })
            .param("eventId", eventId)
            .param("aggregateType", aggregateType)
            .param("orderingKey", orderingKey)
            .param("payload", envelopeJson(eventId))
            .update()
    }

    private fun statusOf(orderingKey: String): Int =
        db
            .sql("SELECT status FROM outbox_message WHERE ordering_key = :orderingKey ORDER BY id LIMIT 1")
            .param("orderingKey", orderingKey)
            .query(Int::class.java)
            .single()

    private fun statusOfEvent(eventId: String): Int =
        db
            .sql("SELECT status FROM outbox_message WHERE event_id = :eventId")
            .param("eventId", eventId)
            .query(Int::class.java)
            .single()

    private fun inboxCount(eventId: String): Int =
        db
            .sql("SELECT COUNT(*) FROM processed_message WHERE event_id = :eventId")
            .param("eventId", eventId)
            .query(Int::class.java)
            .single()

    private fun traceIdOf(orderingKey: String): String =
        db
            .sql("SELECT trace_id FROM outbox_message WHERE ordering_key = :orderingKey ORDER BY id LIMIT 1")
            .param("orderingKey", orderingKey)
            .query(String::class.java)
            .single()

    /** 리스너 스레드에서 보이는 MDC traceId를 붙잡아 두는 테스트 전용 컨슈머. */
    class TraceProbe {
        val captured = LinkedBlockingQueue<String>()

        @KafkaListener(id = ID, topics = [Topics.ORDER], groupId = ID)
        fun onMessage(value: String) {
            captured.add(MDC.get(TraceIdRecordInterceptor.TRACE_ID_MDC_KEY) ?: "")
        }

        companion object {
            // 부재 단언 전에 이 리스너의 파티션 할당을 기다려야 해서 컨테이너를 이름으로 찾음
            const val ID = "trace-probe"
        }
    }

    /** 이벤트 백본 테스트에서만 사용하는 인박스 검증용 소비자. */
    class SyntheticInboxProbe(
        private val objectMapper: ObjectMapper,
        private val processedMessages: ProcessedMessages,
        private val transactionTemplate: TransactionTemplate,
    ) {
        @KafkaListener(id = ID, topics = [Topics.ORDER], groupId = ID)
        fun onMessage(value: String) {
            transactionTemplate.executeWithoutResult {
                val envelope = objectMapper.readValue(value, Envelope::class.java)
                if (envelope.eventType != "SyntheticMessage") return@executeWithoutResult
                if (!processedMessages.markProcessed(ID, envelope.eventId)) return@executeWithoutResult
            }
        }

        companion object {
            const val ID = "synthetic-skeleton"
        }
    }

    @TestConfiguration
    class TraceProbeConfig {
        @Bean
        fun traceProbe() = TraceProbe()

        @Bean
        fun syntheticInboxProbe(
            @Qualifier(MESSAGING_OBJECT_MAPPER) objectMapper: ObjectMapper,
            processedMessages: ProcessedMessages,
            transactionTemplate: TransactionTemplate,
        ) = SyntheticInboxProbe(objectMapper, processedMessages, transactionTemplate)
    }
}
