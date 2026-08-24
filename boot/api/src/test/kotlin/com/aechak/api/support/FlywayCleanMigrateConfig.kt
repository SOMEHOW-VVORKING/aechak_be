package com.aechak.api.support

import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean

/**
 * 마이그레이션 전에 스키마를 clean한다. clean을 연 컨텍스트(KafkaIntegrationTestBase)만 가져다 쓴다.
 *
 * MySQL 컨테이너를 Flyway on/off 컨텍스트가 공유하는데, off 쪽 테스트가 남긴 시드 데이터와
 * V2 categories 시드가 충돌해 컨텍스트 로딩이 실행 순서에 따라 깨지던 문제를 없앤다.
 */
@TestConfiguration(proxyBeanMethods = false)
class FlywayCleanMigrateConfig {
    @Bean
    fun flywayCleanMigrateStrategy(): FlywayMigrationStrategy =
        FlywayMigrationStrategy { flyway ->
            flyway.clean()
            flyway.migrate()
        }
}
