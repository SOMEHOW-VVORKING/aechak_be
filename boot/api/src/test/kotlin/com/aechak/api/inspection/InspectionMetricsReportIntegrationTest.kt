package com.aechak.api.inspection

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.aechak.api.config.InspectionMetricsSchedule
import com.aechak.api.support.IntegrationTestBase
import com.aechak.domain.inspection.ContentInspection
import com.aechak.domain.inspection.InspectionFinding
import com.aechak.domain.inspection.ProductReviewQueueItem
import com.aechak.domain.inspection.enums.InspectionJudgment
import com.aechak.domain.inspection.enums.InspectionPerspectiveKind
import com.aechak.domain.inspection.enums.InspectionTargetType
import com.aechak.domain.inspection.enums.PerspectiveOutcome
import com.aechak.domain.product.category.Category
import com.aechak.domain.product.product.Product
import com.aechak.domain.seller.seller.Seller
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import ch.qos.logback.classic.Logger as LogbackLogger

/**
 * [통합] 지표를 읽는 두 번째 길(주기 로그)을 고정한다.
 *
 * 어드민 화면에도 같은 수치가 있지만 그것은 사람이 열어 봐야 보인다. 적체는 아무도 열어 보지
 * 않을 때가 문제인 지표다 — 쌓인 것을 아무도 안 보면 "사람 검수 단계가 있다"는 말이 거짓이
 * 된다. 그래서 임계를 넘으면 사람이 찾아오지 않아도 로그에 경보로 올라가야 한다.
 *
 * 임계 판정과 경보 문장을 유스케이스 결과가 들고 있다는 사실도 여기서 함께 고정한다. 로그가
 * 다시 판정하면 화면에는 경보가 뜨는데 로그는 조용한 상태, 또는 둘이 다른 문장을 말하는 상태가
 * 생긴다.
 *
 * api가 지표 파사드를 실제로 조립하는지도 이 테스트가 본다. 파사드가 셀러 스캔 밖(facade
 * 패키지)에 있어야 하고 api·어드민 양쪽 스캔 안에 있어야 하는데, 그 배치가 깨지면 여기서
 * 주입 실패로 먼저 드러난다.
 */
@TestPropertySource(
    properties = [
        "inspection.metrics.window-hours=24",
        "inspection.metrics.backlog-warn-count=1",
        "inspection.metrics.backlog-warn-age-minutes=60",
        // 캐시를 끈다. 켜 두면 한 테스트의 스냅샷이 다음 테스트로 새어 수치가 어긋난다.
        "inspection.metrics.snapshot-cache-seconds=0",
    ],
)
class InspectionMetricsReportIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var schedule: InspectionMetricsSchedule

    private val appender = ListAppender<ILoggingEvent>()
    private lateinit var logger: LogbackLogger

    private var productId: Long = 0
    private var sellerUserId: Long = 0
    private var nextReviewRef: Long = 1_000

    @BeforeEach
    fun attachAppender() {
        nextReviewRef = 1_000
        logger = LoggerFactory.getLogger(InspectionMetricsSchedule::class.java) as LogbackLogger
        appender.list.clear()
        appender.start()
        logger.addAppender(appender)

        sellerUserId = createActiveUser()
        tx.execute {
            em.persist(Seller.open(sellerUserId, "애착상회", 0L))
            val root = Category.create(null, Category.ROOT_DEPTH, "강아지", null, 1)
            em.persist(root)
            val mid = Category.create(root, Category.MID_DEPTH, "사료", null, 1)
            em.persist(mid)
            val leaf = Category.create(mid, Category.LEAF_DEPTH, "건사료", null, 1)
            em.persist(leaf)
            val product =
                Product.register(
                    leaf,
                    sellerUserId,
                    "연어 건사료 2kg",
                    "우리 아이 주식으로 좋습니다.",
                    "products/thumbnail.png",
                    25_000L,
                    null,
                    null,
                    null,
                    emptyList(),
                    emptyList(),
                )
            em.persist(product)
            em.flush()
            productId = product.id
        }
    }

    @AfterEach
    fun detachAppender() {
        logger.detachAppender(appender)
        appender.stop()
    }

    @Test
    fun `쌓인 것이 없으면 정보 한 줄만 남는다`() {
        val now = LocalDateTime.now()
        val passed = settleReview(InspectionJudgment.PASS, now)
        finding(passed, InspectionPerspectiveKind.RULE, PerspectiveOutcome.CLEAR, null)
        finding(passed, InspectionPerspectiveKind.DISPLAY_ADS, PerspectiveOutcome.CLEAR, null)
        finding(passed, InspectionPerspectiveKind.CONDUCT_TRADE, PerspectiveOutcome.CLEAR, null)

        schedule.report()

        val line = singleLine()
        assertEquals(Level.INFO, line.level, "경보가 없으면 WARN으로 올리지 않는다")
        assertTrue(line.formattedMessage.contains("설정=1건"), "분모를 적지 않으면 비율만 남아 판단에 못 쓴다")
        // 선별이 꺼져 있으면 승급률의 분모가 0이다. 0%로 적으면 "안 돌았다"와 "전부 승급했다"가 같은 글자가 된다.
        assertTrue(line.formattedMessage.contains("승급률=미측정"), "분모 0을 0%로 적으면 선별을 켤지 말지를 잘못 판단한다")
    }

    @Test
    fun `적체가 임계를 넘으면 경보로 올라가고 문장이 함께 남는다`() {
        val now = LocalDateTime.now()
        val itemId =
            tx.execute {
                val item = ProductReviewQueueItem.openForProduct(productId, sellerUserId, "연어 건사료 2kg", now.minusMinutes(90))
                em.persist(item)
                em.flush()
                item.id
            }!!
        // 대기 시간의 축은 유입 시각이다. 정렬 키(blinded_at)는 새 판정마다 덮이므로
        // 그것으로 재면 매일 재차단되는 행이 영원히 임계에 닿지 않는다
        tx.execute {
            em
                .createNativeQuery("update product_review_queue_items set created_at = :at where id = :id")
                .setParameter("at", now.minusMinutes(90))
                .setParameter("id", itemId)
                .executeUpdate()
        }

        schedule.report()

        val line = singleLine()
        assertEquals(Level.WARN, line.level, "임계를 넘었는데 INFO로 남으면 아무도 적체를 모른다")
        assertTrue(line.formattedMessage.contains("관리자 대기 1건이 임계 1건을 넘었다"), "경보 문장은 결과가 만든 것을 그대로 써야 한다")
        assertTrue(line.formattedMessage.contains("가장 오래된 대기가"), "건수와 시간 임계를 둘 다 적는다")
        assertTrue(line.formattedMessage.contains("관리자대기=1건"), "경보와 수치를 함께 남겨야 로그만으로 상태가 읽힌다")
    }

    private fun singleLine(): ILoggingEvent {
        val events = appender.list.filter { it.loggerName == InspectionMetricsSchedule::class.java.name }
        assertEquals(1, events.size, "주기 로그는 한 줄이다. 실제 로그: ${events.map { it.formattedMessage }}")
        return events.first()
    }

    private fun settleReview(
        judgment: InspectionJudgment,
        settledAt: LocalDateTime,
    ): Long {
        val targetRef = nextReviewRef++
        return tx.execute {
            val inspection =
                ContentInspection.queue(
                    InspectionTargetType.REVIEW,
                    targetRef,
                    "r%063d".format(targetRef),
                    "policy-v1",
                    null,
                )
            em.persist(inspection)
            inspection.settle(judgment, "inspection-v1+policy-v1+triage-v2", null, null, null, settledAt)
            em.flush()
            inspection.id
        }!!
    }

    private fun finding(
        inspectionId: Long,
        perspective: InspectionPerspectiveKind,
        outcome: PerspectiveOutcome,
        skipReason: String?,
    ) {
        tx.execute {
            em.persist(InspectionFinding.of(inspectionId, perspective, outcome, skipReason, null))
            em.flush()
        }
    }
}
