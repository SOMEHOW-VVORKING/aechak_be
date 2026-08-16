package com.aechak.api.support

import com.aechak.application.file.port.FileStorage
import com.aechak.application.user.verification.support.VerificationCodeGenerator
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.testcontainers.containers.GenericContainer
import org.testcontainers.mysql.MySQLContainer

/**
 * 통합 테스트 공용 인프라. 데이터 정리기와 외부 어댑터 Fake 빈을 등록한다.
 *
 * 컨테이너는 빈이 아니라 companion에서 직접 start한다(70 §3 예외).
 * 빈으로 등록하면 컨텍스트가 닫힐 때 컨테이너도 함께 멈추는데, 테스트 베이스가
 * 둘(IntegrationTestBase, KafkaIntegrationTestBase)이 된 지금은 한쪽 컨텍스트의
 * 종료가 다른 쪽의 DB를 끊어버릴 수 있기 때문이다. 수동 start한 컨테이너는
 * JVM이 끝날 때 Testcontainers가 정리한다.
 */
@TestConfiguration(proxyBeanMethods = false)
class IntegrationTestConfig {
    companion object {
        private const val REDIS_PORT = 6379

        // 운영 MySQL 버전 고정 태그. JVM당 1회만 뜨고, 두 베이스의 컨텍스트가 공유한다.
        private val mysql = MySQLContainer("mysql:8.4").apply { start() }

        // 인증 코드·발송 제한 저장소. MySQL과 같은 이유로 빈이 아니라 수동 start.
        private val redis = GenericContainer("redis:7.4").withExposedPorts(REDIS_PORT).apply { start() }

        /** 각 테스트 베이스의 @DynamicPropertySource가 호출해 datasource·redis를 이 컨테이너들로 연결한다. */
        @JvmStatic
        fun registerContainers(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", mysql::getJdbcUrl)
            registry.add("spring.datasource.username", mysql::getUsername)
            registry.add("spring.datasource.password", mysql::getPassword)
            registry.add("spring.data.redis.host", redis::getHost)
            registry.add("spring.data.redis.port") { redis.getMappedPort(REDIS_PORT) }
        }
    }

    @Bean
    fun databaseCleaner(jdbcTemplate: JdbcTemplate) = DatabaseCleaner(jdbcTemplate)

    /**
     * Flyway를 켠 컨텍스트(KafkaIntegrationTestBase)는 마이그레이션 전에 스키마를 clean한다.
     * MySQL 컨테이너를 Flyway on/off 컨텍스트가 공유하는데, off 쪽 테스트가 남긴 시드 데이터와
     * V2 categories 시드가 충돌해 컨텍스트 로딩이 실행 순서에 따라 깨지던 문제를 없앤다.
     * Flyway를 끈 컨텍스트에는 Flyway 빈이 없어 이 전략이 호출되지 않는다.
     */
    @Bean
    fun flywayCleanMigrateStrategy(): FlywayMigrationStrategy =
        FlywayMigrationStrategy { flyway ->
            flyway.clean()
            flyway.migrate()
        }

    /** 실 어댑터면 펫 사진 승격이 자격증명 없이 AWS로 나감 */
    @Bean
    @Primary
    fun fakeFileStorage(): FileStorage = FakeFileStorage()

    /** 프로덕션은 SecureRandom이라, 통합 테스트에선 코드가 결정적이어야 confirm까지 관통한다 — 고정 생성기로 덮는다. */
    @Bean
    @Primary
    fun fixedVerificationCodeGenerator(): VerificationCodeGenerator = VerificationCodeGenerator { "000000" }
}
