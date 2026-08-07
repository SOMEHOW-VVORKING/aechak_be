package com.aechak.api.support

import com.aechak.infra.kafka.Topics
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.transaction.support.TransactionTemplate

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
@Import(IntegrationTestConfig::class)
@EmbeddedKafka(partitions = 1, topics = [Topics.ORDER, Topics.ORDER_DLT])
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

    @BeforeEach
    fun cleanDatabase() = cleaner.truncateAll()

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun datasource(registry: DynamicPropertyRegistry) = IntegrationTestConfig.registerDatasource(registry)
    }
}
