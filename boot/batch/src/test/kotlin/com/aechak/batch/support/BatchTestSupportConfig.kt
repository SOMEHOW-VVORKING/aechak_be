package com.aechak.batch.support

import com.aechak.api.support.DatabaseCleaner
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.jdbc.core.JdbcTemplate

/**
 * batch 통합 테스트 공용 부품. api 테스트의 데이터 정리기만 가져다 쓴다 —
 * api의 IntegrationTestConfig 전체를 들이면 배치가 쓰지 않는 Fake 빈까지 조립을 요구한다.
 */
@TestConfiguration(proxyBeanMethods = false)
class BatchTestSupportConfig {
    @Bean
    fun databaseCleaner(jdbcTemplate: JdbcTemplate) = DatabaseCleaner(jdbcTemplate)
}
