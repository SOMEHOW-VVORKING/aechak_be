package com.aechak.api.support

import com.aechak.infra.kafka.Topics
import org.apache.kafka.clients.admin.Admin
import org.apache.kafka.clients.admin.AdminClientConfig
import org.apache.kafka.clients.admin.ListConsumerGroupOffsetsSpec
import org.apache.kafka.clients.admin.OffsetSpec
import org.apache.kafka.common.TopicPartition
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.kafka.config.KafkaListenerEndpointRegistry
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.transaction.support.TransactionTemplate
import java.time.Duration

/**
 * 이벤트 백본(Kafka) 통합 테스트의 공용 베이스
 *
 * IntegrationTestBase와 갈라진 이유: 내장 Kafka 브로커와 flyway가 필요한데,
 * 이 설정들은 컨텍스트 캐시 키에 들어가서 기존 베이스에 얹으면 어차피 컨텍스트가
 * 갈라진다. 그래서 Kafka가 필요한 테스트끼리 이 베이스를 공유해 부팅을 상수로 묶는다.
 * MySQL 컨테이너는 IntegrationTestBase 쪽 컨텍스트와 공유한다(수동 start라 안전).
 */
@SpringBootTest(
    properties = [
        "spring.jpa.hibernate.ddl-auto=create",
        // 팀 공통 테스트 설정은 flyway를 꺼둔다. 엔티티 테이블은 ddl-auto가 만들어주기 때문인데,
        // 아웃박스와 인박스 테이블은 엔티티가 아니라 마이그레이션으로만 생긴다. 그래서 여기서는 켠다.
        "spring.flyway.enabled=true",
        // 공유 MySQL을 flyway off 컨텍스트와 함께 쓰므로, 마이그레이션 전에 clean할 수 있게 연다
        // (IntegrationTestConfig의 clean-then-migrate 전략과 짝).
        "spring.flyway.clean-disabled=false",
        "spring.kafka.bootstrap-servers=\${spring.embedded.kafka.brokers}",
        "spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer",
        "spring.kafka.producer.value-serializer=org.apache.kafka.common.serialization.StringSerializer",
        // 잘못된 토픽으로 보내 실패를 재현하는 테스트가 기본값(60초)만큼 기다리지 않도록 줄인다
        "spring.kafka.producer.properties.max.block.ms=3000",
        "spring.kafka.consumer.key-deserializer=org.apache.kafka.common.serialization.StringDeserializer",
        "spring.kafka.consumer.value-deserializer=org.apache.kafka.common.serialization.StringDeserializer",
        "spring.kafka.consumer.auto-offset-reset=earliest",
        "spring.kafka.listener.ack-mode=RECORD",
    ],
)
@Import(IntegrationTestConfig::class, FlywayCleanMigrateConfig::class)
// 토픽을 이 베이스 하나에 모아 둔다. 테스트마다 다른 목록을 선언하면 컨텍스트 캐시 키가
// 갈라져 스프링 부팅이 테스트 묶음마다 반복되고 CI 시간이 늘어난다.
@EmbeddedKafka(
    partitions = 1,
    topics = [
        Topics.ORDER,
        Topics.ORDER_DLT,
        Topics.REVIEW,
        Topics.REVIEW_DLT,
        Topics.INSPECTION,
        Topics.INSPECTION_DLT,
        Topics.VERDICT,
        Topics.VERDICT_DLT,
    ],
)
abstract class KafkaIntegrationTestBase {
    @Autowired
    protected lateinit var tx: TransactionTemplate

    @Autowired
    protected lateinit var db: JdbcClient

    /** 내장 브로커 주소. 테스트가 직접 소비자를 만들 때 쓴다. */
    @Value("\${spring.embedded.kafka.brokers}")
    protected lateinit var brokers: String

    @Autowired
    private lateinit var cleaner: DatabaseCleaner

    @Autowired
    private lateinit var listenerRegistry: KafkaListenerEndpointRegistry

    /**
     * 테이블을 비우기 전에 컨슈머가 밀린 레코드를 다 처리하게 만든다.
     *
     * 발행은 afterCommit에 즉시 나가는데(OutboxMessagePublisher) 소비는 비동기다. 그래서 앞 테스트가
     * 띄운 이벤트가 다음 테스트의 truncate 뒤에 처리될 수 있다. truncate는 AUTO_INCREMENT까지 되돌리므로
     * 다음 테스트의 review·user id가 앞 테스트와 똑같이 1부터 다시 나온다 — 밀린 이벤트의 멱등키
     * (EARN:REVIEW_REWARD:<reviewId>)가 다음 테스트의 것과 충돌해, 앞 테스트 금액이 먼저 적립되고
     * 뒤 테스트의 적립은 "이미 처리됨"으로 건너뛰어진다. 원장 건수는 1건이라 멱등 단언은 통과하는데
     * 잔액만 틀리는, 기다려도 안 풀리는 실패가 된다(run #588).
     *
     * 그래서 테스트마다 "기다려 주기"를 손으로 넣는 규율에 기대지 않고, 여기서 기구로 막는다.
     * 커밋된 오프셋이 끝 오프셋에 닿았다는 것은 ack-mode=RECORD 기준으로 리스너의 트랜잭션이
     * 이미 커밋됐다는 뜻이라, 그 뒤에 비우면 넘어올 일이 없다.
     */
    @BeforeEach
    fun drainConsumersThenCleanDatabase() {
        drainConsumers()
        cleaner.truncateAll()
    }

    private fun drainConsumers() {
        val assignments =
            listenerRegistry.listenerContainers
                .mapNotNull { container ->
                    val groupId = container.groupId ?: return@mapNotNull null
                    val assigned = container.assignedPartitions?.toSet().orEmpty()
                    if (assigned.isEmpty()) null else groupId to assigned
                }.toMap()
        if (assignments.isEmpty()) return

        Admin.create(mapOf(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG to brokers)).use { admin ->
            val deadline = System.nanoTime() + DRAIN_TIMEOUT.toNanos()
            while (true) {
                val pending = pendingRecords(admin, assignments)
                if (pending == 0L) return
                check(System.nanoTime() < deadline) {
                    "내장 Kafka 컨슈머가 ${DRAIN_TIMEOUT.toSeconds()}초 안에 밀린 ${pending}건을 처리하지 못했다"
                }
                Thread.sleep(DRAIN_POLL_MILLIS)
            }
        }
    }

    /** 그룹마다 (끝 오프셋 - 커밋된 오프셋)의 합. auto-offset-reset=earliest라 미커밋은 0부터 센다. */
    private fun pendingRecords(
        admin: Admin,
        assignments: Map<String, Set<TopicPartition>>,
    ): Long {
        val committed =
            admin
                .listConsumerGroupOffsets(assignments.keys.associateWith { ListConsumerGroupOffsetsSpec() })
                .let { result -> assignments.keys.associateWith { result.partitionsToOffsetAndMetadata(it).get() } }
        val endOffsets =
            admin
                .listOffsets(assignments.values.flatten().associateWith { OffsetSpec.latest() })
                .all()
                .get()

        return assignments.entries.sumOf { (groupId, partitions) ->
            partitions.sumOf { partition ->
                val end = endOffsets[partition]?.offset() ?: 0L
                val current = committed[groupId]?.get(partition)?.offset() ?: 0L
                (end - current).coerceAtLeast(0L)
            }
        }
    }

    companion object {
        private val DRAIN_TIMEOUT = Duration.ofSeconds(30)
        private const val DRAIN_POLL_MILLIS = 50L

        @JvmStatic
        @DynamicPropertySource
        fun containers(registry: DynamicPropertyRegistry) = IntegrationTestConfig.registerContainers(registry)
    }
}
