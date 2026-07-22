package com.aechak.api.support

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.testcontainers.mysql.MySQLContainer

/**
 * 통합 테스트 공용 인프라. 데이터 정리기와 외부 어댑터 Fake 빈을 등록한다.
 *
 * MySQL 컨테이너는 빈이 아니라 companion에서 직접 start한다(70 §3 예외).
 * 빈으로 등록하면 컨텍스트가 닫힐 때 컨테이너도 함께 멈추는데, 테스트 베이스가
 * 둘(IntegrationTestBase, KafkaIntegrationTestBase)이 된 지금은 한쪽 컨텍스트의
 * 종료가 다른 쪽의 DB를 끊어버릴 수 있기 때문이다. 수동 start한 컨테이너는
 * JVM이 끝날 때 Testcontainers가 정리한다.
 */
@TestConfiguration(proxyBeanMethods = false)
class IntegrationTestConfig {
    companion object {
        // 운영 MySQL 버전 고정 태그. JVM당 1회만 뜨고, 두 베이스의 컨텍스트가 공유한다.
        private val mysql = MySQLContainer("mysql:8.4").apply { start() }

        /** 각 테스트 베이스의 @DynamicPropertySource가 호출해 datasource를 이 컨테이너로 연결한다. */
        @JvmStatic
        fun registerDatasource(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", mysql::getJdbcUrl)
            registry.add("spring.datasource.username", mysql::getUsername)
            registry.add("spring.datasource.password", mysql::getPassword)
        }
    }

    @Bean
    fun databaseCleaner(jdbcTemplate: JdbcTemplate) = DatabaseCleaner(jdbcTemplate)
}
