package com.aechak.admin.support;

import com.aechak.application.file.port.FileStorage;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.mysql.MySQLContainer;

/**
 * 어드민 통합 테스트 공용 인프라 — 실 MySQL 컨테이너 + 데이터 정리기.
 * 컨테이너를 static에서 직접 start하는 이유는 api IntegrationTestConfig와 동일(70 §3 예외).
 */
@TestConfiguration(proxyBeanMethods = false)
public class IntegrationTestConfig {

    // 운영 MySQL 버전 고정 태그. JVM당 1회만 뜨고 컨텍스트들이 공유한다.
    private static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");

    static {
        MYSQL.start();
    }

    /** 테스트 베이스의 @DynamicPropertySource가 호출해 datasource를 이 컨테이너로 연결한다. */
    public static void registerDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Bean
    public DatabaseCleaner databaseCleaner(JdbcTemplate jdbcTemplate) {
        return new DatabaseCleaner(jdbcTemplate);
    }

    /** 실 어댑터면 다운로드 URL 발급이 자격증명 없는 AWS 서명 호출로 가서 깨진다. */
    @Bean
    @Primary
    public FileStorage fakeFileStorage() {
        return new FakeFileStorage();
    }

    /** 게이트 통과를 매핑된 핸들러로 검증하기 위한 고정 표적 — 미매핑 404는 catch-all(90000)로 새서 판별이 흐려진다. */
    @Bean
    public SecurityProbeController securityProbeController() {
        return new SecurityProbeController();
    }
}
