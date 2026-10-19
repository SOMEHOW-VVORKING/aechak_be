package com.aechak.batch.support

import com.aechak.api.support.DatabaseCleaner
import com.aechak.api.support.FakePaymentGateway
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.jdbc.core.JdbcTemplate

/**
 * batch 통합 테스트가 추가로 필요한 빈만 등록한다 — api의 IntegrationTestConfig를 통째로 들이면
 * 배치가 쓰지 않는 소셜 로그인·인증번호 Fake까지 따라온다.
 */
@TestConfiguration(proxyBeanMethods = false)
class BatchTestSupportConfig {
    @Bean
    fun databaseCleaner(jdbcTemplate: JdbcTemplate) = DatabaseCleaner(jdbcTemplate)

    /** 실 어댑터면 만료 배치의 결제 결과 조회가 시크릿 없이 실 포트원으로 나간다 */
    @Bean
    @Primary
    fun fakePaymentGateway(): FakePaymentGateway = FakePaymentGateway()
}
