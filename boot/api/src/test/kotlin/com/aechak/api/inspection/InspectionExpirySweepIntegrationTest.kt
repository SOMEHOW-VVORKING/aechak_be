package com.aechak.api.inspection

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.aechak.api.support.IntegrationTestBase
import com.aechak.application.inspection.usecase.InspectionUseCase
import com.aechak.domain.inspection.ContentInspection
import com.aechak.domain.inspection.InspectionFinding
import com.aechak.domain.inspection.enums.InspectionPerspectiveKind
import com.aechak.domain.inspection.enums.InspectionTargetType
import com.aechak.domain.inspection.repository.ContentInspectionRepository
import com.aechak.domain.inspection.repository.InspectionFindingRepository
import com.aechak.domain.product.category.Category
import com.aechak.domain.product.product.Product
import com.aechak.domain.product.stats.ProductStats
import com.aechak.domain.seller.seller.Seller
import com.aechak.infra.persistence.inspection.InspectionFindingRepositoryAdapter
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.test.context.TestPropertySource
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import ch.qos.logback.classic.Logger as LogbackLogger

/**
 * [통합] 기한 정리 스윕의 잠금 규약을 고정한다. **순서와 범위 둘 다**다.
 *
 * 순서: 판정 적용과 관리자 결정은 **검수 행 → 상품 행**으로 잠근다. 스윕이 반대로 잡으면 같은
 * (검수, 상품) 쌍에 동시에 닿는 두 트랜잭션이 교착한다. 하이버네이트가 실제로 낸 SQL로
 * 발행 순서를 단언한다.
 *
 * 경계: 배치 전체를 한 트랜잭션에 두면 **처리를 끝낸 상품의 잠금을 끝까지 쥔다.** 상품 둘에
 * 걸친 배치가 상품 행을 잠그는 다른 쓰기(어드민 결정·판정 수신·다른 인스턴스의 스윕)와 고리를
 * 만들고, 후보 한 건의 실패나 낙관적 락 예외가 그 주기에 닫은 전부를 되돌린다.
 *
 * 범위: 순서를 맞춰도 **넓게 잠그면 교착한다.** 조건 조회를 잠금 조회로 두면 걸 인덱스가 없어
 * 전체 스캔이 되고, `FOR UPDATE` 전체 스캔은 읽은 행마다 넥스트키 잠금을 잡고 마지막에
 * supremum 간극까지 잠근다. 그 간극이 다음 INSERT 자리라서 상품 등록·수정이 세워지고, 전혀
 * 다른 상품을 만지는 셀러 쓰기와도 고리가 닫힌다. 그 둘을 실제로 재현해 단언한다.
 *
 * 노출 반영이 켜져 있을 때만 스윕이 상품 행에 닿는다. 기한 정리도 관리자 검토 큐에 올리고,
 * 큐 등재가 상품 행을 잠그기 때문이다. 교착이 생기는 설정이라 여기서 그대로 켠다.
 */
@TestPropertySource(properties = ["inspection.gate-enabled=true"])
class InspectionExpirySweepIntegrationTest : IntegrationTestBase() {
    /**
     * 관점 기록을 한 건만 실패시키는 포이즌 빈.
     *
     * "한 건 실패가 배치 전체를 롤백하지 않는다"는 잠금 타임아웃 없이 결정적으로 재현해야
     * 한다. 실패 지점을 쓰기 포트로 끼워 넣으면 그 한 건만 터지고 나머지 후보는 그대로 돈다.
     */
    @TestConfiguration
    class PoisonedFindingConfig {
        @Bean
        @Primary
        fun poisonedFindings(delegate: InspectionFindingRepositoryAdapter) = PoisonedFindingRepository(delegate)
    }

    class PoisonedFindingRepository(
        private val delegate: InspectionFindingRepository,
    ) : InspectionFindingRepository {
        var poisonedInspectionId: Long? = null

        override fun upsert(finding: InspectionFinding): InspectionFinding {
            if (finding.inspectionId == poisonedInspectionId) error("관점 기록이 실패한 검수 한 건")
            return delegate.upsert(finding)
        }

        override fun findByInspectionId(inspectionId: Long): List<InspectionFinding> = delegate.findByInspectionId(inspectionId)

        override fun findOne(
            inspectionId: Long,
            perspective: InspectionPerspectiveKind,
        ): InspectionFinding? = delegate.findOne(inspectionId, perspective)
    }

    @Autowired
    private lateinit var inspections: ContentInspectionRepository

    @Autowired
    private lateinit var inspectionUseCase: InspectionUseCase

    @Autowired
    private lateinit var poisonedFindings: PoisonedFindingRepository

    private val sqlLogger = LoggerFactory.getLogger("org.hibernate.SQL") as LogbackLogger
    private val captured = ListAppender<ILoggingEvent>()
    private var previousLevel: Level? = null
    private var productId = 0L
    private var sellerUserId = 0L
    private var leafCategoryId = 0L

    @BeforeEach
    fun seedAndCaptureSql() {
        poisonedFindings.poisonedInspectionId = null
        sellerUserId = createActiveUser()
        tx.execute {
            em.persist(Seller.open(sellerUserId, "애착상회", 0L))
            val root = Category.create(null, Category.ROOT_DEPTH, "강아지", null, 1)
            em.persist(root)
            val mid = Category.create(root, Category.MID_DEPTH, "사료", null, 1)
            em.persist(mid)
            val leaf = Category.create(mid, Category.LEAF_DEPTH, "건사료", null, 1)
            em.persist(leaf)
            leafCategoryId = leaf.id
        }
        productId = persistProduct("연어 건사료 2kg")

        previousLevel = sqlLogger.level
        captured.start()
        sqlLogger.addAppender(captured)
        sqlLogger.level = Level.DEBUG
    }

    @AfterEach
    fun stopCaptureSql() {
        sqlLogger.detachAppender(captured)
        sqlLogger.level = previousLevel
        captured.stop()
    }

    @Test
    fun `기한 정리는 검수 행을 잠그고 시작해 상품 잠금보다 앞선다`() {
        givenOverdue("a".repeat(64))
        captured.list.clear()

        assertEquals(1, inspectionUseCase.expireOverdue())

        val statements = capturedSql()
        val firstInspectionLock =
            statements.indexOfFirst { it.contains("content_inspections") && it.contains("for update") }
        val firstProductLock = statements.indexOfFirst { it.contains(" products ") && it.contains("for update") }
        assertTrue(firstInspectionLock >= 0, "스윕이 검수 행을 잠그지 않았다. 발행 SQL=$statements")
        assertTrue(firstProductLock >= 0, "스윕이 상품 행을 잠그지 않았다. 발행 SQL=$statements")
        assertTrue(
            statements[firstInspectionLock].contains("skip locked"),
            "잠금만 걸고 건너뛰지 않으면 다중 인스턴스의 두 스윕이 같은 묶음에서 서로를 기다린다",
        )
        assertTrue(
            firstInspectionLock < firstProductLock,
            "상품을 먼저 잠그면 판정 적용 경로와 역순이 되어 교착한다. 발행 SQL=$statements",
        )
        // 범위 규약: 조건 조회는 잠그지 않는다. 잠금은 처리할 행에만 걸려야 한다.
        // 조건 조회를 잠금 조회로 두면 걸 인덱스가 없어 테이블 전체와 그 뒤 간극까지 잠긴다.
        val candidateQuery = statements.first { it.startsWith("select") && it.contains("content_inspections") }
        assertTrue(
            !candidateQuery.contains("for update"),
            "기한 후보 조회가 잠금 조회다 — 검수 테이블을 넓게 잠가 상품 등재가 막힌다. SQL=$candidateQuery",
        )
    }

    @Test
    fun `다른 인스턴스가 들고 있는 검수는 건너뛰고 잠금이 풀리면 다시 집는다`() {
        val inspection = givenOverdue("b".repeat(64))
        val locked = CountDownLatch(1)
        val failure = AtomicReference<Throwable?>(null)
        val holder =
            Thread {
                runCatching {
                    tx.execute {
                        em
                            .createNativeQuery("select id from content_inspections where id = :id for update")
                            .setParameter("id", inspection.id)
                            .singleResult
                        locked.countDown()
                        Thread.sleep(HOLD_MILLIS)
                    }
                }.onFailure(failure::set)
            }
        holder.start()
        assertTrue(locked.await(5, TimeUnit.SECONDS), "다른 트랜잭션이 검수 행을 잠그지 못했다")

        assertEquals(0, inspectionUseCase.expireOverdue(), "남이 들고 있는 행을 같이 집으면 한쪽이 배치 전체를 롤백한다")

        holder.join()
        assertNull(failure.get())
        assertEquals(1, inspectionUseCase.expireOverdue(), "건너뛴 행이 다음 주기에 집히지 않으면 영구 미처리로 남는다")
    }

    /**
     * 잠금 범위 재현 ①: 스윕이 도는 동안 새 검수 등재가 막히지 않아야 한다.
     *
     * 잠금 조회의 조건(`processing_state <> SETTLED`, `judgment is null`, `requested_at <`)에 쓸
     * 인덱스가 없어 전체 스캔이 된다. `FOR UPDATE` 전체 스캔은 읽은 행마다 넥스트키 잠금을
     * 잡고 마지막에 supremum 간극까지 잠근다. 그 간극이 곧 **다음 INSERT 자리**다. 상품
     * 등록·수정은 검수 행 INSERT 로 끝나므로, 스윕 한 번이 그 사이의 모든 등록·수정을 세운다.
     *
     * 스윕 트랜잭션을 열어 둔 채로 다른 연결에서 등재해 본다. 좁게 잠그면 즉시 들어가고,
     * 넓게 잠그면 스윕이 커밋할 때까지(기본 잠금 대기 50초) 들어가지 못한다.
     */
    @Test
    fun `기한 정리가 도는 중에도 새 검수 등재가 막히지 않는다`() {
        givenOverdue("a".repeat(64))
        val sweepHoldsLocks = CountDownLatch(1)
        val queued = CountDownLatch(1)
        val failure = AtomicReference<Throwable?>(null)
        val other =
            Thread {
                runCatching {
                    assertTrue(sweepHoldsLocks.await(5, TimeUnit.SECONDS), "스윕이 잠금을 잡지 못했다")
                    tx.execute { queueInspection("c".repeat(64)) }
                }.onFailure(failure::set)
                queued.countDown()
            }
        other.start()

        // 스윕을 바깥 트랜잭션에 합류시켜 커밋을 미룬다. 실제 운영에서도 배치 한 묶음이
        // 커밋될 때까지 잠금이 살아 있으므로 같은 상황이다.
        tx.execute {
            assertEquals(1, inspectionUseCase.expireOverdue())
            sweepHoldsLocks.countDown()
            assertTrue(
                queued.await(QUEUE_WAIT_SECONDS, TimeUnit.SECONDS),
                "스윕이 검수 테이블을 넓게 잠가 새 검수 등재가 막혔다 — 상품 등록·수정이 스윕마다 세워진다",
            )
        }

        other.join()
        assertNull(failure.get())
    }

    /**
     * 잠금 범위 재현 ②: 스윕과 셀러 쓰기가 교착하지 않아야 한다.
     *
     * 순서는 양쪽 모두 검수 행 → 상품 행이라 역순 교착은 없다. 그런데도 교착하는 이유가
     * 범위다. 스윕이 **자기가 처리할 행뿐 아니라 테이블 전체와 그 뒤 간극까지** 잠그면,
     * 전혀 다른 상품을 만지는 셀러의 INSERT 가 그 간극에 걸린다. 그 셀러가 상품 행을 들고
     * 있고 스윕이 같은 상품 행을 기다리면 고리가 닫힌다.
     *
     * 셀러는 자기 상품 행을 잠근 뒤 검수를 등재한다(등록·수정 경로의 순서다). 스윕은 그
     * 사이에 돌아 같은 상품 행을 기다린다. 좁게 잠그면 셀러의 INSERT 가 통과해 커밋되고
     * 스윕이 이어 받는다. 넓게 잠그면 둘이 서로를 기다려 InnoDB 가 한쪽을 죽인다.
     */
    @Test
    fun `기한 정리가 셀러 쓰기와 교착하지 않는다`() {
        givenOverdue("d".repeat(64))
        val productLocked = CountDownLatch(1)
        val sweepStarted = CountDownLatch(1)
        val sellerFailure = AtomicReference<Throwable?>(null)
        val seller =
            Thread {
                runCatching {
                    tx.execute {
                        em
                            .createNativeQuery("select id from products where id = :id for update")
                            .setParameter("id", productId)
                            .singleResult
                        productLocked.countDown()
                        // 스윕이 검수 테이블 잠금을 잡고 상품 행을 기다리기 시작할 틈을 준다.
                        // 스윕이 막혀 있는 동안에는 래치를 내려 줄 수 없어 시간으로 센다.
                        assertTrue(sweepStarted.await(5, TimeUnit.SECONDS), "스윕이 시작하지 못했다")
                        Thread.sleep(SWEEP_REACH_MILLIS)
                        queueInspection("e".repeat(64))
                    }
                }.onFailure(sellerFailure::set)
            }
        seller.start()
        assertTrue(productLocked.await(5, TimeUnit.SECONDS), "셀러가 상품 행을 잠그지 못했다")

        sweepStarted.countDown()
        val sweepFailure = runCatching { inspectionUseCase.expireOverdue() }.exceptionOrNull()

        seller.join()
        assertNull(
            sellerFailure.get(),
            "셀러의 검수 등재가 스윕의 넓은 잠금과 교착했다 — 셀러는 자기 상품만 만졌는데 남의 스윕에 죽는다",
        )
        assertNull(sweepFailure, "스윕이 셀러 쓰기와 교착했다 — 배치 전체가 롤백되고 큐 등재까지 사라진다")
    }

    /**
     * 후보를 고른 뒤 판정이 도착한 건은 아예 건드리지 않는다.
     *
     * 덮어쓰기 자체는 엔티티의 낙관적 락이 막는다. 막는 방식이 예외라서, 배치 전체를 한
     * 트랜잭션에 두면 그 예외가 그 주기에 닫은 건 전부를 되돌린다. AI 가 10분 넘게 죽었다가
     * 복구되면 밀린 판정과 밀린 후보가 겹치므로, 스윕이 주기마다 롤백되며 큐 등재가 하나도
     * 올라가지 않는다. 장애가 길어질수록 안전장치가 멈추는 쪽이다.
     *
     * 그래서 잠근 뒤 기한 조건을 다시 보고 건너뛴다. 잠금 조회는 스냅샷이 아니라 최신 커밋을
     * 읽으므로 재확인이 실제로 그 판정을 본다. 낙관적 락은 최후의 심판으로 남는다.
     */
    @Test
    fun `후보를 고른 뒤 판정이 도착한 건은 건드리지 않는다`() {
        val inspection = givenOverdue("f".repeat(64))
        val settled = CountDownLatch(1)
        val failure = AtomicReference<Throwable?>(null)
        val judge =
            Thread {
                runCatching {
                    tx.execute {
                        em
                            .createNativeQuery(
                                "update content_inspections set judgment = 'PASS', processing_state = 'SETTLED' where id = :id",
                            ).setParameter("id", inspection.id)
                            .executeUpdate()
                    }
                }.onFailure(failure::set)
                settled.countDown()
            }

        tx.execute {
            // 읽기 뷰를 먼저 고정한다. 엔티티를 적재하지 않는 조회여야 한다 — 영속성 컨텍스트에
            // 올라가 있으면 잠금 조회가 행만 잠그고 캐시된 옛 상태를 돌려준다.
            em.createNativeQuery("select count(*) from content_inspections").singleResult
            judge.start()
            assertTrue(settled.await(5, TimeUnit.SECONDS), "판정이 커밋되지 않았다")

            assertEquals(
                0,
                inspectionUseCase.expireOverdue(),
                "후보 스냅샷만 믿고 닫으려 들었다 — 낙관적 락 예외가 그 주기에 닫은 건 전부를 되돌린다",
            )
        }

        judge.join()
        assertNull(failure.get())
        val judgment =
            tx.execute {
                em
                    .createNativeQuery("select judgment from content_inspections where id = :id")
                    .setParameter("id", inspection.id)
                    .singleResult as String
            }
        assertEquals("PASS", judgment, "판정이 미판정으로 덮였다 — 낙관적 락까지 뚫렸다는 뜻이다")
    }

    /**
     * 배치가 여러 상품에 걸칠 때 **처리를 끝낸 상품의 잠금을 들고 다음 상품을 기다리지 않는다.**
     *
     * 후보가 id 순이어도 상품 순서는 섞인다 — 한 상품에 과기한 미판정 행이 둘 있을 수 있고,
     * 두 인스턴스의 스윕은 `SKIP LOCKED`로 서로 다른 행을 집는다. 배치 전체를 한 트랜잭션에
     * 두면 스윕 A가 상품 Y를 들고 X를 기다리는 동안 스윕 B가 X를 들고 Y를 기다리는 모양이
     * 생기고, InnoDB가 한쪽을 죽여 **배치 전체가 롤백된다.** 미판정 큐 등재까지 함께 사라지는데,
     * 그것이 행 단위 잠금으로 바꿀 때 막겠다고 한 바로 그 손실이다.
     *
     * 여기서는 뒤 후보의 상품 행을 잡아 둔 다른 트랜잭션이, 스윕이 이미 처리한 앞 후보의
     * 상품 행을 만지려 들게 해 같은 고리를 만든다. 한 건씩 커밋하면 앞 상품의 잠금이 이미
     * 풀려 있어 고리가 닫히지 않는다.
     */
    @Test
    fun `배치가 상품 둘에 걸쳐도 앞 상품의 잠금을 들고 뒤 상품을 기다리지 않는다`() {
        val otherProductId = persistProduct("오리 건사료 2kg")
        val first = givenOverdue("g".repeat(64), productId)
        val second = givenOverdue("h".repeat(64), otherProductId)
        val otherLocked = CountDownLatch(1)
        val failure = AtomicReference<Throwable?>(null)
        val holder =
            Thread {
                runCatching {
                    tx.execute {
                        // 뒤 후보의 상품 행을 잡는다. 스윕은 앞 후보를 처리한 뒤 이 행을 기다린다.
                        lockProductRow(otherProductId)
                        otherLocked.countDown()
                        // 스윕이 앞 후보를 처리하고 이 행 대기까지 가는 틈을 준다. 스윕이 막혀
                        // 있는 동안에는 래치를 내려 줄 수 없어 시간으로 센다.
                        Thread.sleep(SWEEP_REACH_MILLIS)
                        // 앞 후보의 상품 행. 배치 내내 잠금을 쥐는 구현이면 여기서 고리가 닫힌다.
                        lockProductRow(productId)
                    }
                }.onFailure(failure::set)
            }
        holder.start()
        assertTrue(otherLocked.await(5, TimeUnit.SECONDS), "다른 트랜잭션이 뒤 후보의 상품 행을 잠그지 못했다")

        val sweepFailure = runCatching { inspectionUseCase.expireOverdue() }.exceptionOrNull()

        holder.join()
        assertNull(
            sweepFailure,
            "스윕이 처리를 끝낸 상품의 잠금을 배치 내내 쥐고 있어 교착했다 — 배치가 롤백되며 미판정 등재까지 사라진다",
        )
        assertNull(failure.get(), "스윕이 이미 처리한 상품을 만지는 쓰기가 스윕의 잠금에 죽는다")
        assertEquals("UNJUDGED", storedJudgment(first.id), "앞 후보가 닫히지 않았다")
        assertEquals("UNJUDGED", storedJudgment(second.id), "뒤 후보가 닫히지 않았다")
    }

    /**
     * 한 건이 실패해도 나머지 배치는 남는다.
     *
     * 배치를 한 트랜잭션에 두면 후보 하나의 실패가 **그 주기에 처리한 전부**를 되돌린다.
     * 기한 정리는 큐 등재를 함께 하는 경로라, 되돌아간 만큼 사람이 볼 기회도 사라진다.
     * 실패한 건은 다음 주기에 다시 집히면 되고, 그 사이 다른 건이 볼모가 될 이유는 없다.
     */
    @Test
    fun `한 건이 실패해도 나머지 배치는 남는다`() {
        val otherProductId = persistProduct("오리 건사료 2kg")
        val poisoned = givenOverdue("i".repeat(64), productId)
        val healthy = givenOverdue("j".repeat(64), otherProductId)
        poisonedFindings.poisonedInspectionId = poisoned.id

        val expired = runCatching { inspectionUseCase.expireOverdue() }

        assertNull(
            expired.exceptionOrNull(),
            "후보 한 건의 실패가 스윕 호출 전체를 깨면 그 주기에 처리한 것이 모두 사라진다",
        )
        assertEquals(1, expired.getOrNull(), "실패한 건은 세지 않고 나머지는 처리해야 한다")
        assertNull(storedJudgment(poisoned.id), "실패한 건이 닫힌 것으로 남으면 다음 주기가 다시 집지 못한다")
        assertEquals("UNJUDGED", storedJudgment(healthy.id), "한 건의 실패가 멀쩡한 건의 미판정 등재까지 롤백했다")
    }

    private fun capturedSql(): List<String> = captured.list.map { it.formattedMessage.lowercase() }

    /** 상품 등록·수정이 하는 일. 검수 행 INSERT 하나다 */
    private fun queueInspection(
        hash: String,
        targetRef: Long = productId,
    ): ContentInspection =
        inspections.save(
            ContentInspection.queue(
                targetType = InspectionTargetType.PRODUCT,
                targetRef = targetRef,
                contentHash = hash,
                policyVersion = "policy-v1",
                targetVersionNo = 1,
            ),
        )

    /** 상품 하나를 심는다. 상품이 둘 이상인 배치를 만들어야 잠금 고리가 닫히는 경우가 보인다 */
    private fun persistProduct(name: String): Long =
        tx.execute {
            val product =
                Product.register(
                    category = em.find(Category::class.java, leafCategoryId),
                    sellerId = sellerUserId,
                    name = name,
                    description = "우리 아이 주식으로 좋습니다.",
                    representativeImageKey = "products/thumbnail.png",
                    regularPrice = 25_000L,
                    discountPrice = null,
                    discountStartAt = null,
                    discountEndAt = null,
                    additionalImageKeys = emptyList(),
                    detailImageKeys = emptyList(),
                )
            em.persist(product)
            em.persist(ProductStats.create(product.id))
            product.id
        }!!

    private fun storedJudgment(inspectionId: Long): String? =
        tx.execute {
            em
                .createNativeQuery("select judgment from content_inspections where id = :id")
                .setParameter("id", inspectionId)
                .singleResult as String?
        }

    /** 상품 행을 잠근다. 셀러 쓰기와 다른 스윕이 그 상품에 닿는 순간을 흉내 낸다 */
    private fun lockProductRow(id: Long) {
        em
            .createNativeQuery("select id from products where id = :id for update")
            .setParameter("id", id)
            .singleResult
    }

    /** 기한이 지난 미판정 검수 하나. 테스트가 10분을 기다릴 수는 없어 요청 시각을 과거로 민다 */
    private fun givenOverdue(
        hash: String,
        targetRef: Long = productId,
    ): ContentInspection {
        val inspection = tx.execute { queueInspection(hash, targetRef) }!!
        tx.execute {
            em
                .createNativeQuery("update content_inspections set requested_at = DATE_SUB(NOW(6), INTERVAL 60 MINUTE) where id = :id")
                .setParameter("id", inspection.id)
                .executeUpdate()
        }
        return inspection
    }

    companion object {
        /** 잠금을 들고 있는 시간. 건너뛰지 않는 구현이면 이 시간만큼 기다린 뒤 같은 행을 집는다 */
        private const val HOLD_MILLIS = 2_000L

        /**
         * 스윕이 잠금을 들고 있는 동안 새 등재를 기다려 주는 시간.
         *
         * 좁게 잠그면 밀리초 안에 들어간다. 넓게 잠그면 기본 잠금 대기(50초)까지 못 들어가므로
         * 이 값이 둘을 가른다. 넉넉히 두어도 판정이 흔들리지 않는다.
         */
        private const val QUEUE_WAIT_SECONDS = 5L

        /** 스윕이 검수 잠금을 잡고 상품 행 대기까지 가는 데 주는 시간 */
        private const val SWEEP_REACH_MILLIS = 1_500L
    }
}
