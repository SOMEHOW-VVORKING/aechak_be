package com.aechak.api.support

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
 * 모든 통합 테스트의 공용 베이스
 */
@SpringBootTest(
    properties = [
        "spring.jpa.hibernate.ddl-auto=create",
    ],
)
@Import(IntegrationTestConfig::class)
abstract class IntegrationTestBase {
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
        fun datasource(registry: DynamicPropertyRegistry) = IntegrationTestConfig.registerDatasource(registry)
    }
}
