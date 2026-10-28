package com.aechak.batch.support

import com.aechak.api.support.DatabaseCleaner
import com.aechak.api.support.FakePaymentGateway
import com.aechak.api.support.IntegrationTestConfig
import com.aechak.api.support.SettlementFeePolicyFixture
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
    protected lateinit var paymentGateway: FakePaymentGateway

    @Autowired
    private lateinit var cleaner: DatabaseCleaner

    @BeforeEach
    fun resetState() {
        cleaner.truncateAll()
        paymentGateway.clear()
    }

    /**
     * 구매확정을 태우는 배치가 요구하는 전역 수수료율 정책 1행을 심는다.
     * 배치는 Flyway를 들지 않고 매 테스트 전에 테이블을 비우므로 요율 행이 남아 있지 않다 —
     * 확정이 정산 원장을 만들 때 요율 정책을 찾지 못하면 그 건의 확정 자체가 되돌아간다.
     */
    protected fun seedGlobalSettlementFeePolicy() {
        tx.execute {
            em.persist(SettlementFeePolicyFixture.global())
            em.flush()
        }
    }

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun containers(registry: DynamicPropertyRegistry) = IntegrationTestConfig.registerContainers(registry)
    }
}
