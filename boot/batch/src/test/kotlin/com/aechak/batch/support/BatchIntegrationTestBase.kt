package com.aechak.batch.support

import com.aechak.api.support.DatabaseCleaner
import com.aechak.api.support.IntegrationTestConfig
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.transaction.support.TransactionTemplate

/**
 * batch 통합 테스트 공용 베이스. 컨테이너(MySQL·Redis)는 api test-fixtures의 IntegrationTestConfig가 띄운 것을 공유한다 —
 * 모듈마다 컨테이너를 따로 띄우지 않는다. 스키마는 ddl-auto(create)가 만든다(배치는 Flyway를 들지 않는다).
 */
@SpringBootTest(
    properties = [
        "spring.jpa.hibernate.ddl-auto=create",
    ],
)
@Import(BatchTestSupportConfig::class)
abstract class BatchIntegrationTestBase {
    @PersistenceContext
    protected lateinit var em: EntityManager

    @Autowired
    protected lateinit var tx: TransactionTemplate

    @Autowired
    private lateinit var cleaner: DatabaseCleaner

    @BeforeEach
    fun cleanDatabase() = cleaner.truncateAll()

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun containers(registry: DynamicPropertyRegistry) = IntegrationTestConfig.registerContainers(registry)
    }
}
