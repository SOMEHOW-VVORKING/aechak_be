package com.aechak.api.inspection

import com.aechak.api.support.IntegrationTestBase
import com.aechak.application.inspection.facade.InspectionFacade
import com.aechak.application.inspection.usecase.command.ApplyVerdictCommand
import com.aechak.domain.inspection.ProductReviewQueueItem
import com.aechak.domain.inspection.repository.ProductReviewQueueRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.time.LocalDateTime
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * [통합] 서로 다른 상품이 **처음** 큐에 오르는 두 판정이 서로를 막지 않는지 본다.
 *
 * 열린 행 조회는 잠금 조회다. 열린 행이 없을 때 InnoDB는 (target_type, target_id, status)
 * 비유일 인덱스에서 레코드가 아니라 **간극**을 잠그고, 새 상품의 target_id는 언제나 그 인덱스의
 * 꼬리 간극에 떨어진다. 그 간극이 곧 다음 INSERT 자리라서, 처음 큐에 오르는 상품 둘이 서로의
 * INSERT를 기다리다 교착한다 — 예외가 아니라 기본 경우다. 판정 소비자는 동시성 3으로 돌고
 * 기한 정리도 후보마다 같은 경로를 탄다.
 *
 * 그래서 판정 적용 경로는 READ COMMITTED로 돈다. 그 격리 수준에서는 잠금 조회가 간극을 잠그지
 * 않고(레코드 잠금만), 매 구문이 최신 커밋을 읽어 잠금 조회의 목적인 "먼저 커밋한 쪽이 넣은
 * 행을 본다"도 함께 지켜진다.
 *
 * 이 테스트의 트랜잭션은 격리 수준을 **판정 적용 경로의 선언에서 읽어 온다**. 그 선언이 사라지면
 * 여기도 DB 기본값(REPEATABLE READ)으로 떨어져 교착이 다시 난다 — 선언이 장식이 아니라는 것을
 * 이 테스트가 들고 있는 방식이다.
 *
 * 순서는 장벽으로 못박는다. 두 트랜잭션이 각자 잠금 조회를 끝낸 **뒤에** 둘 다 INSERT로 들어가야
 * 이 모양이 재현되고, 그 순서를 강제하지 않으면 겹치지 않은 실행이 그냥 통과해 버린다.
 */
class ReviewQueueFirstEnqueueConcurrencyIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var reviewQueue: ProductReviewQueueRepository

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    @Test
    fun `서로 다른 상품이 처음 큐에 오르는 두 판정은 교착하지 않는다`() {
        val barrier = CyclicBarrier(2)
        val pool = Executors.newFixedThreadPool(2)
        val failures =
            try {
                val tasks =
                    PRODUCT_IDS.map { productId ->
                        pool.submit<Throwable?> { runCatching { enqueueFirstRow(productId, barrier) }.exceptionOrNull() }
                    }
                tasks.mapNotNull { it.get(60, TimeUnit.SECONDS) }
            } finally {
                pool.shutdownNow()
            }

        assertEquals(
            emptyList(),
            failures.map { "${it::class.simpleName}: ${it.message?.lines()?.first()}" },
            "처음 큐에 오르는 상품 둘이 서로를 막으면 판정 하나가 롤백되고 기한 정리 한 건이 실패한다",
        )
        PRODUCT_IDS.forEach { assertNotNull(reviewQueue.findPendingByProductId(it), "productId=$it 의 행이 열리지 않았다") }
    }

    /** 판정 적용이 하는 것과 같은 순서 — 열린 행을 잠금 조회하고, 없으면 행을 연다 */
    private fun enqueueFirstRow(
        productId: Long,
        barrier: CyclicBarrier,
    ) {
        verdictPathTx().execute {
            reviewQueue.findPendingByProductIdForUpdate(productId)
            barrier.await(30, TimeUnit.SECONDS)
            reviewQueue.save(
                ProductReviewQueueItem.openForProduct(productId, OWNER_ID, "연어 건사료 2kg", LocalDateTime.now()),
            )
        }
    }

    /** 판정 적용 경로가 선언한 격리 수준으로 도는 트랜잭션 */
    private fun verdictPathTx(): TransactionTemplate {
        val declared =
            InspectionFacade::class.java
                .getMethod("applyVerdict", ApplyVerdictCommand::class.java)
                .getAnnotation(Transactional::class.java)
                ?.isolation ?: Isolation.DEFAULT
        return TransactionTemplate(transactionManager).apply { isolationLevel = declared.value() }
    }

    private companion object {
        /** 큐에 아무것도 없는 상태에서 쓰는 두 상품. 둘 다 인덱스의 같은 꼬리 간극에 떨어진다 */
        val PRODUCT_IDS = listOf(9_000L, 9_001L)
        const val OWNER_ID = 7_000L
    }
}
