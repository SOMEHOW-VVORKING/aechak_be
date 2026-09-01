package com.aechak.batch.support

import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.mysql.MySQLContainer

@SpringBootTest(
    properties = [
        "spring.jpa.hibernate.ddl-auto=create",
    ],
)
abstract class BatchIntegrationTestBase {
    companion object {
        // 컨테이너를 빈으로 두면 컨텍스트가 닫힐 때 함께 멈춤. JVM 종료 시 Testcontainers가 정리함
        private val mysql = MySQLContainer("mysql:8.4").apply { start() }

        @JvmStatic
        @DynamicPropertySource
        fun datasource(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", mysql::getJdbcUrl)
            registry.add("spring.datasource.username", mysql::getUsername)
            registry.add("spring.datasource.password", mysql::getPassword)
        }
    }
}
