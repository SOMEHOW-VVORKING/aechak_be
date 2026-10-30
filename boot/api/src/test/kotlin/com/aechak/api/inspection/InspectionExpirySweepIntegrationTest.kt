package com.aechak.api.inspection

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.aechak.api.support.IntegrationTestBase
import com.aechak.application.inspection.usecase.InspectionUseCase
import com.aechak.application.inspection.usecase.command.ApplyVerdictCommand
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
import com.aechak.message.inspection.ContentVerdictMessage
import com.aechak.message.inspection.InspectionPerspective
import com.aechak.message.inspection.InspectionVerdict
import com.aechak.message.inspection.PerspectiveResult
import com.aechak.message.inspection.Violation
import com.aechak.message.inspection.ViolationCategory
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.test.context.TestPropertySource
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import ch.qos.logback.classic.Logger as LogbackLogger
import com.aechak.message.inspection.InspectionTargetType as ContractTargetType
import com.aechak.message.inspection.PerspectiveOutcome as ContractOutcome

/**
 * [통합] 기한 정리 스윕의 잠금 대상과 경계를 고정한다.
 *
 * 대상: 기한 만료는 상품 상태도 관리자 검토 큐도 건드리지 않는다. 모델이 답을 주지 않은 것은
 * 노출을 내릴 근거가 아니고, 사람이 풀 것도 확정할 것도 없는 행을 큐에 쌓으면 모델 장애마다
 * 운영자가 수백 건을 떠안는다. 그래서 이 경로가 잠그는 것은 **검수 행 하나뿐**이고, 그 사실을
 * 하이버네이트가 실제로 낸 SQL 로 단언한다. 상품 행을 잠그는 스윕은 그 결정을 되돌린 것이다.
 *
 * 경계: 배치 전체를 한 트랜잭션에 두면 후보 한 건의 실패나 낙관적 락 예외가 그 주기에 닫은
 * 전부를 되돌린다. 후보 조회가 잠금 없는 스냅샷이라 고른 뒤 판정이 도착한 행이 섞이고, AI 가
 * 기한보다 오래 죽었다 복구되면 그 겹침이 주기마다 전량 롤백이 되어 기한 정리가 영구히 멈춘다.
 *
 * 범위: 조건 조회를 잠금 조회로 두면 걸 인덱스가 없어 전체 스캔이 되고, `FOR UPDATE` 전체
 * 스캔은 읽은 행마다 넥스트키 잠금을 잡고 마지막에 supremum 간극까지 잠근다. 그 간극이 다음
 * INSERT 자리라서 상품 등록·수정이 세워지고, 전혀 다른 상품을 만지는 셀러 쓰기까지 스윕에
 * 걸려 죽는다. 그 둘을 실제로 재현해 단언한다.
 *
 * 노출 반영 스위치를 켠 채로 돈다. 꺼 두면 "상품 행을 잠그지 않는다"가 스위치 때문에 참인지
 * 경로 때문에 참인지 가를 수 없다. 켜 두면 그 단언이 경로에 대한 것이 된다.
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

    /** 스윕이 후보 한 건을 삼키고 경고로 흘리는 것을 보려면 그쪽 로거도 잡아야 한다 */
    private val facadeLogger = LoggerFactory.getLogger(FACADE_LOGGER) as LogbackLogger
    private val facadeLogs = ListAppender<ILoggingEvent>()
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
        facadeLogs.start()
        facadeLogger.addAppender(facadeLogs)
    }

    @AfterEach
    fun stopCaptureSql() {
        sqlLogger.detachAppender(captured)
        sqlLogger.level = previousLevel
        captured.stop()
        facadeLogger.detachAppender(facadeLogs)
        facadeLogs.stop()
    }

    @Test
    fun `기한 정리는 검수 행만 잠그고 상품 행은 건드리지 않는다`() {
        givenOverdue("a".repeat(64))
        captured.list.clear()

        assertEquals(1, inspectionUseCase.expireOverdue())

        val statements = capturedSql()
        val firstInspectionLock =
            statements.indexOfFirst { it.contains("content_inspections") && it.contains("for update") }
        assertTrue(firstInspectionLock >= 0, "스윕이 검수 행을 잠그지 않았다. 발행 SQL=$statements")
        assertTrue(
            statements[firstInspectionLock].contains("skip locked"),
            "잠금만 걸고 건너뛰지 않으면 다중 인스턴스의 두 스윕이 같은 묶음에서 서로를 기다린다",
        )
        // 기한 만료는 상품을 건드리지 않는다. 노출 상태를 바꾸지도, 관리자 검토 큐에 올리지도
        // 않으므로 상품 행을 잠글 이유가 없다. 잠그는 구현은 그 결정을 되돌린 것이다.
        assertTrue(
            statements.none { it.contains(" products ") && it.contains("for update") },
            "스윕이 상품 행을 잠갔다 — 기한 만료가 상품이나 큐를 건드리고 있다. 발행 SQL=$statements",
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
     * 여기서 증명하는 것은 **스윕이 셀러가 쥔 상품 행을 기다리지도 않는다**는 쪽이다. 기한
     * 만료는 상품을 건드리지 않으므로 셀러가 자기 상품 행을 들고 있어도 스윕은 그 행에
     * 닿지 않고 지나간다. 고리를 만들 한쪽 변이 아예 없다.
     *
     * 남는 변은 검수 테이블이다. 스윕이 자기가 처리할 행뿐 아니라 테이블 전체와 그 뒤
     * 간극까지 잠그면, 전혀 다른 상품을 만지는 셀러의 검수 INSERT 가 그 간극에 걸려 선다.
     * 셀러는 자기 상품 행을 잠근 뒤 검수를 등재한다(등록·수정 경로의 순서다). 좁게 잠그면
     * 둘이 서로를 모른 채 끝나고, 넓게 잠그면 셀러 쪽이 스윕을 기다린다.
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
            "셀러의 검수 등재가 스윕의 넓은 잠금에 걸렸다 — 셀러는 자기 상품만 만졌는데 남의 스윕에 죽는다",
        )
        assertNull(sweepFailure, "스윕이 셀러가 쥔 상품 행을 기다렸다 — 기한 만료는 상품에 닿을 일이 없다")
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
     * 판정 적용이 그 검수를 들고 있으면 기한 정리는 **건드리지 않고 건너뛴다.**
     *
     * 관점 기록의 쓰기 포트는 원자 upsert 가 아니라 조회 후 저장이고, 같은 검수를 두
     * 트랜잭션이 동시에 쓰는 창이 호출부의 검수 행 잠금으로 닫힌다는 전제를 문서로 적어
     * 두었다. 판정 적용은 그 전제를 지킨다 — 적기 전에 검수 행을 잠근다. 기한 정리도 같은
     * 잠금을 잡아야 그 전제가 경로 둘에서 모두 선다.
     *
     * 잠그지 않으면 스윕이 남의 트랜잭션이 쓰고 있는 검수를 스냅샷으로 읽어 그대로 닫으려
     * 든다. 그 쓰기는 결국 막힌다 — 낙관적 락이 최후의 심판으로 남아 있다. 다만 막는 방식이
     * 예외라서 후보 한 건이 실패로 떨어지고, 그 실패가 경고 로그로 흘러간다. 정상 경로에서
     * 그 예외를 보지 않는 것이 잠금을 넣은 이유다. 그래서 단언이 결과와 경고 둘 다다.
     *
     * 관점 행도 함께 본다. `(검수, 관점)` 유일키가 중복을 막더라도, 한 행을 두 경로가 함께
     * 쓰면 모델이 적은 결과가 기한 표식으로 덮인다. 끝난 뒤 그 행은 하나이고 내용은 판정
     * 쪽의 것이어야 한다.
     */
    @Test
    fun `판정 적용이 검수 행을 들고 있으면 기한 정리가 예외 없이 건너뛴다`() {
        val inspection = givenOverdue("k".repeat(64))
        val verdictApplied = CountDownLatch(1)
        val sweepDone = CountDownLatch(1)
        val verdictFailure = AtomicReference<Throwable?>(null)
        val judge =
            Thread {
                runCatching {
                    tx.execute {
                        inspectionUseCase.applyVerdict(ApplyVerdictCommand(passVerdictFor(inspection)))
                        verdictApplied.countDown()
                        // 스윕이 같은 검수를 집어 보는 동안 잠금을 쥔다. 막힌 스윕은 래치를
                        // 내려 줄 수 없으므로 기다림에 한도를 둔다.
                        sweepDone.await(SWEEP_WAIT_SECONDS, TimeUnit.SECONDS)
                    }
                }.onFailure(verdictFailure::set)
            }
        judge.start()
        assertTrue(verdictApplied.await(5, TimeUnit.SECONDS), "판정이 적히지 않았다")
        facadeLogs.list.clear()

        val expired = runCatching { inspectionUseCase.expireOverdue() }
        sweepDone.countDown()
        judge.join()

        assertNull(verdictFailure.get(), "판정 적용이 스윕 때문에 깨졌다 — 안전장치가 본 경로를 죽이면 안 된다")
        assertNull(expired.exceptionOrNull(), "스윕 호출 전체가 깨졌다")
        assertEquals(0, expired.getOrNull(), "판정 적용이 들고 있는 행을 미판정으로 닫으려 들었다")
        assertTrue(
            facadeLogs.list.none { it.level == Level.WARN && it.throwableProxy != null },
            "스윕이 건너뛰지 않고 남이 쓰는 검수를 덮으려 들어 예외를 받았다 — 정상 경로에서 볼 예외가 아니다. " +
                "로그=${facadeLogs.list.map { it.formattedMessage }}",
        )
        assertEquals("PASS", storedJudgment(inspection.id), "판정이 미판정으로 덮였다")
        val allRows =
            tx.execute {
                em
                    .createNativeQuery(
                        "select skip_reason from inspection_findings where inspection_id = :id and perspective = 'ALL'",
                    ).setParameter("id", inspection.id)
                    .resultList
            }!!
        assertEquals(1, allRows.size, "같은 관점 행이 둘이 됐다. 행=$allRows")
        assertNull(allRows.single(), "모델이 적은 관점 결과가 기한 표식으로 덮였다")
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

    /**
     * 기한을 넘겨 닫은 뒤에 도착한 판정은 버려진다. 버린 사실이 남아야 한다.
     *
     * 기한 만료가 상품도 큐도 건드리지 않기로 한 결정의 직접 귀결이다. 10분 1초에 모델이
     * 위반을 찾아내도 그 판정은 반영되지 않고, 같은 내용으로 다시 요청해도 같은 내용·같은
     * 정책의 검수를 재사용하는 조회가 그 닫힌 행을 그대로 돌려줘서 다시 검수되지도 않는다.
     * 그러면 "위반을 찾으면 반려"가 기한 경계 밖에서 조용히 무효가 된다.
     *
     * 되살리는 것은 이번 범위가 아니다. 지금 고정하는 것은 **무엇을 버렸는지가 남는다**까지다.
     * 디버그로 흘리면 운영 로그 수준에서 사라져 장애가 몇 건을 삼켰는지 알 길이 없다.
     */
    @Test
    fun `기한을 넘겨 닫은 뒤 도착한 위반 판정은 버린 사실을 경고로 남긴다`() {
        val inspection = givenOverdue("l".repeat(64))
        assertEquals(1, inspectionUseCase.expireOverdue())
        facadeLogs.list.clear()

        inspectionUseCase.applyVerdict(ApplyVerdictCommand(flagVerdictFor(inspection)))

        assertEquals("UNJUDGED", storedJudgment(inspection.id), "닫힌 판정이 덮였다")
        assertEquals(0, openReviewCount(), "기한 만료 건은 큐에 오르지 않는다는 결정이 깨졌다")
        val dropped =
            facadeLogs.list.filter { it.level == Level.WARN && it.formattedMessage.contains("기한을 넘겨 닫은 검수") }
        assertEquals(
            1,
            dropped.size,
            "버려진 판정이 경고로 남지 않았다 — 모델이 찾아낸 위반이 반영도 안 되고 흔적도 없다. " +
                "로그=${facadeLogs.list.map { "${it.level}:${it.formattedMessage}" }}",
        )
        assertTrue(
            dropped.single().formattedMessage.contains("inspectionId=${inspection.id}"),
            "어느 검수의 판정을 버렸는지가 없으면 뒤쫓을 수 없다. 로그=${dropped.single().formattedMessage}",
        )
    }

    /**
     * 같은 판정이 두 번 와도 경고로 올리지 않는다.
     *
     * 카프카 재전달은 정상이고 멱등 가드가 그것을 받아 내는 자리다. 가드 전체를 경고로 올리면
     * 재전달마다 경고가 서서 정말 버려진 판정이 그 안에 묻힌다.
     */
    @Test
    fun `같은 판정이 두 번 와도 경고로 올리지 않는다`() {
        val inspection = givenOverdue("m".repeat(64))
        inspectionUseCase.applyVerdict(ApplyVerdictCommand(passVerdictFor(inspection)))
        facadeLogs.list.clear()

        inspectionUseCase.applyVerdict(ApplyVerdictCommand(passVerdictFor(inspection)))

        assertTrue(
            facadeLogs.list.none { it.level == Level.WARN },
            "정상 재전달이 경고가 됐다. 로그=${facadeLogs.list.map { it.formattedMessage }}",
        )
    }

    private fun openReviewCount(): Long =
        tx.execute {
            em
                .createNativeQuery(
                    "select count(*) from product_review_queue_items where target_id = :id and status = 'PENDING_REVIEW'",
                ).setParameter("id", productId)
                .singleResult as Long
        }!!

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

    /**
     * 통과 판정 한 건. 관점은 `ALL` 하나다.
     *
     * 기한 정리가 쓰는 관점 행과 같은 칸이라, 두 경로가 한 행을 놓고 만나는 모양을 만든다.
     * 팬아웃을 끄면 생산자가 실제로 이 값으로 보낸다.
     */
    private fun passVerdictFor(inspection: ContentInspection): ContentVerdictMessage =
        ContentVerdictMessage(
            inspectionId = inspection.id,
            targetType = ContractTargetType.PRODUCT,
            targetRef = inspection.targetRef,
            contentHash = inspection.contentHash,
            verdict = InspectionVerdict.PASS,
            perspectives = listOf(PerspectiveResult(InspectionPerspective.ALL, ContractOutcome.CLEAR)),
            violations = emptyList(),
            sellerNotice = null,
            calls = emptyList(),
            promptVersion = "inspection-v1+policy-v1",
            schemaVersion = ContentVerdictMessage.SCHEMA_VERSION,
            decidedAt = Instant.parse("2026-10-19T03:00:05Z"),
        )

    /** 위반 판정 한 건. 모델이 읽었다는 증거(관점 결과)를 실어 게이트가 버리지 않게 한다 */
    private fun flagVerdictFor(inspection: ContentInspection): ContentVerdictMessage =
        passVerdictFor(inspection).copy(
            verdict = InspectionVerdict.FLAG,
            perspectives = listOf(PerspectiveResult(InspectionPerspective.ALL, ContractOutcome.VIOLATION)),
            violations =
                listOf(
                    Violation(
                        category = ViolationCategory.MEDICAL_CLAIM,
                        perspective = InspectionPerspective.ALL,
                        spanStart = null,
                        spanEnd = null,
                        quote = "관절염 치료에 효과",
                        suggestion = null,
                        confidence = null,
                    ),
                ),
            sellerNotice = "치료 효능 표현을 고쳐 주세요.",
        )

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

        /** 스윕이 검수 잠금을 잡고 셀러 쓰기와 만나는 데 주는 시간 */
        private const val SWEEP_REACH_MILLIS = 1_500L

        /**
         * 판정 적용이 잠금을 쥐고 스윕을 기다려 주는 시간.
         *
         * 건너뛰는 구현이면 스윕이 밀리초 안에 끝나 래치가 먼저 내려온다. 덮어쓰려 드는
         * 구현은 이 시간만큼 막혀 있다가 잠금이 풀린 뒤 낙관적 락 예외를 받는다.
         */
        private const val SWEEP_WAIT_SECONDS = 5L

        /** 스윕이 후보 한 건의 실패를 적는 로거 */
        private const val FACADE_LOGGER = "com.aechak.application.inspection.facade.InspectionFacade"
    }
}
