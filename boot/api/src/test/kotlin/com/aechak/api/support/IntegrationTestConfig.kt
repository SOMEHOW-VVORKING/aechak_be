package com.aechak.api.support

import com.aechak.application.file.port.FileStorage
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.jdbc.core.JdbcTemplate
import org.testcontainers.containers.GenericContainer
import org.testcontainers.mysql.MySQLContainer

/**
 * 통합 테스트 공용 인프라. MySQL 컨테이너와 데이터 정리기를 빈으로 등록
 *
 * 컨테이너는 companion 인스턴스 하나라 JVM당 1회만 뜸. 단 이 성질은 전 테스트가
 * 컨텍스트 하나를 공유할 때만 유지됨. 컨텍스트가 갈라지면 한쪽이 닫힐 때
 * 공유 컨테이너까지 멈출 수 있음. 외부 어댑터(PG, SMS 등)의 Fake 빈도 여기에 등록함.
 */
@TestConfiguration(proxyBeanMethods = false)
class IntegrationTestConfig {
    companion object {
        // 운영 MySQL 버전 고정 태그
        private val mysql = MySQLContainer("mysql:8.4")

        // 인증 코드·발송 제한 저장소(SLR-01) — 이미지명 기반 ServiceConnection 매칭이라 name 지정 필수
        private val redis = GenericContainer("redis:7.4").withExposedPorts(6379)
    }

    @Bean
    @ServiceConnection
    fun mysqlContainer(): MySQLContainer = mysql

    @Bean
    @ServiceConnection(name = "redis")
    fun redisContainer(): GenericContainer<*> = redis

    @Bean
    fun databaseCleaner(jdbcTemplate: JdbcTemplate) = DatabaseCleaner(jdbcTemplate)

    /** 실 어댑터면 펫 사진 승격이 자격증명 없이 AWS로 나감 */
    @Bean
    @Primary
    fun fakeFileStorage(): FileStorage = FakeFileStorage()
}
