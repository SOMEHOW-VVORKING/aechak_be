package com.aechak.api.inspection

import com.aechak.api.support.IntegrationTestBase
import com.aechak.application.inspection.usecase.InspectionUseCase
import com.aechak.application.inspection.usecase.command.ApplyVerdictCommand
import com.aechak.application.product.product.usecase.ProductUseCase
import com.aechak.application.product.product.usecase.query.ProductSearchQuery
import com.aechak.domain.inspection.ContentInspection
import com.aechak.domain.inspection.ProductReviewQueueItem
import com.aechak.domain.inspection.enums.InspectionTargetType
import com.aechak.domain.inspection.enums.ReviewQueueStatus
import com.aechak.domain.inspection.repository.ContentInspectionRepository
import com.aechak.domain.product.category.Category
import com.aechak.domain.product.product.Product
import com.aechak.domain.product.product.enums.InspectionStatus
import com.aechak.domain.product.stats.ProductStats
import com.aechak.domain.seller.seller.Seller
import com.aechak.message.inspection.ContentVerdictMessage
import com.aechak.message.inspection.InspectionPerspective
import com.aechak.message.inspection.InspectionVerdict
import com.aechak.message.inspection.PerspectiveResult
import com.aechak.message.inspection.Violation
import com.aechak.message.inspection.ViolationCategory
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource
import java.time.Instant
import java.time.LocalDateTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import com.aechak.message.inspection.InspectionTargetType as ContractTargetType
import com.aechak.message.inspection.PerspectiveOutcome as ContractOutcome

/**
 * [통합] 노출 게이트를 고정한다.
 *
 * 여기서 잡는 것은 판정이 **구매자 조회에 실제로 반영되는가**다. 조회 조건 자체는 이미
 * 승인 상태만 통과시키고 있었고 비어 있던 것은 그 상태를 바꾸는 쪽이었다.
 *
 * 함께 잡는 것이 하나 더 있다. **판정을 받지 못한 건은 막지 않는다.** 모델이 보지 못한 것을
 * 위반으로 다루면 모델 장애가 그대로 매출 장애가 된다.
 *
 * 위반 판정은 상품을 바로 반려하고 관리자 검토 큐에 올린다. 관리자는 그 행에서 오탐을 풀거나
 * 반려를 확정한다. 관리자가 반려한 상품은 고친 내용이 통과해도 관리자가 다시 본다.
 *
 * 되돌리는 것은 **증거가 있는 통과**뿐이다. 모델이 그 내용을 읽은 적 없는 통과는 판정 실패와
 * 같은 자리에 두고 상품도 큐도 건드리지 않는다. 선별이 일부러 건너뛴 통과는 장애가 아니라
 * 판단이라서 정당한 통과로 남긴다 — 그쪽까지 막으면 선별 계층의 비용 절감이 사라진다.
 */
@TestPropertySource(properties = ["inspection.gate-enabled=true"])
class InspectionGateIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var inspections: ContentInspectionRepository

    @Autowired
    private lateinit var inspectionUseCase: InspectionUseCase

    @Autowired
    private lateinit var productUseCase: ProductUseCase

    private var sellerUserId = 0L
    private var productId = 0L
    private lateinit var publicId: String

    @BeforeEach
    fun seed() {
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
                    category = leaf,
                    sellerId = sellerUserId,
                    name = "연어 건사료 2kg",
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
            productId = product.id
            publicId = product.publicId
        }
    }

    @Test
    fun `위반 판정이면 반려되어 구매자 조회에서 사라지고 관리자 검토 큐에 오른다`() {
        assertTrue(listedByBuyer(), "판정 전에는 보여야 한다. 등록 직후 숨기는 정책이 아니다")
        val inspection = givenQueued()

        applyVerdict(inspection, InspectionVerdict.FLAG)

        assertEquals(InspectionStatus.REJECTED, storedStatus())
        assertFalse(listedByBuyer(), "위반 판정을 받은 상품이 목록에 남아 있으면 게이트가 장식이다")
        assertEquals(
            listOf(ReviewQueueStatus.PENDING_REVIEW),
            queueStatuses(),
            "행이 없으면 관리자가 오탐을 풀어 줄 길이 없다",
        )
    }

    @Test
    fun `고친 내용이 통과하면 노출이 되돌아오고 큐 행은 관리자 확인 없이 닫힌다`() {
        applyVerdict(givenQueued(hash = "a".repeat(64)), InspectionVerdict.FLAG)
        assertFalse(listedByBuyer())

        applyVerdict(givenQueued(hash = "b".repeat(64)), InspectionVerdict.PASS)

        assertEquals(InspectionStatus.APPROVED, storedStatus())
        assertTrue(listedByBuyer(), "되돌리는 경로가 없으면 한 번 걸린 상품을 셀러가 영구히 되살릴 수 없다")
        assertEquals(
            listOf(ReviewQueueStatus.SUPERSEDED),
            queueStatuses(),
            "고친 내용이 통과했는데 행이 열려 있으면 관리자가 이미 끝난 일을 다시 본다",
        )
    }

    @Test
    fun `판정 과정이 실패한 건은 노출을 막지 않고 큐에도 올리지 않는다`() {
        applyVerdict(givenQueued(), InspectionVerdict.FAILED)

        assertEquals(InspectionStatus.APPROVED, storedStatus())
        assertTrue(listedByBuyer(), "판정에 실패한 것을 위반으로 다루면 모델 장애가 곧 매출 장애가 된다")
        assertEquals(emptyList<ReviewQueueStatus>(), queueStatuses(), "숨기지 않은 상품까지 올리면 관리자가 풀 것도 확정할 것도 없는 행을 본다")
    }

    @Test
    fun `기한이 지나 미판정으로 닫힌 건은 노출을 막지 않고 큐에도 올리지 않는다`() {
        val inspection = givenQueued()
        backdateRequestedAt(inspection.id, minutes = 60)

        assertEquals(1, inspectionUseCase.expireOverdue())

        assertEquals(InspectionStatus.APPROVED, storedStatus())
        assertTrue(listedByBuyer(), "아무도 보지 않은 상품을 막으면 외부 지연이 그대로 노출 중단이 된다")
        assertEquals(emptyList<ReviewQueueStatus>(), queueStatuses())
    }

    @Test
    fun `관리자가 반려한 상품은 고친 내용이 통과해도 관리자가 다시 본다`() {
        changeStatus(InspectionStatus.REJECTED)
        givenAdminDecisions(ReviewQueueStatus.REJECTED)

        applyVerdict(givenQueued(), InspectionVerdict.PASS)

        assertEquals(InspectionStatus.REJECTED, storedStatus(), "사람이 내린 반려를 AI 통과가 풀면 안 된다")
        assertEquals(listOf(ReviewQueueStatus.REJECTED, ReviewQueueStatus.PENDING_REVIEW), queueStatuses())
    }

    @Test
    fun `예전에 관리자가 반려했어도 마지막 관리자 결정이 승인이면 AI 반려는 통과로 풀린다`() {
        givenAdminDecisions(ReviewQueueStatus.REJECTED, ReviewQueueStatus.APPROVED)
        applyVerdict(givenQueued(hash = "a".repeat(64)), InspectionVerdict.FLAG)

        applyVerdict(givenQueued(hash = "b".repeat(64)), InspectionVerdict.PASS)

        assertEquals(InspectionStatus.APPROVED, storedStatus(), "지난 반려 이력만 보고 묶어 두면 셀러가 고쳐도 관리자 없이는 못 푼다")
        assertTrue(listedByBuyer())
    }

    @Test
    fun `같은 상품에 판정이 또 와도 열린 행은 하나다`() {
        applyVerdict(givenQueued(hash = "a".repeat(64)), InspectionVerdict.FLAG)
        applyVerdict(givenQueued(hash = "b".repeat(64)), InspectionVerdict.FLAG)

        assertEquals(
            listOf(ReviewQueueStatus.PENDING_REVIEW),
            queueStatuses(),
            "열린 행이 둘이면 관리자가 같은 상품을 두 번 처리하고 하나는 409로 남는다",
        )
    }

    @Test
    fun `증거 없는 통과는 AI 반려를 풀지 않고 열린 행도 닫지 않는다`() {
        applyVerdict(givenQueued(hash = "a".repeat(64)), InspectionVerdict.FLAG)

        applyVerdict(
            givenQueued(hash = "b".repeat(64)),
            InspectionVerdict.PASS,
            perspectives =
                listOf(
                    PerspectiveResult(InspectionPerspective.DISPLAY_ADS, ContractOutcome.UNCHECKED, "RATE_LIMITED"),
                    PerspectiveResult(InspectionPerspective.CONDUCT_TRADE, ContractOutcome.UNCHECKED, "RATE_LIMITED"),
                ),
        )

        assertEquals(
            InspectionStatus.REJECTED,
            storedStatus(),
            "모델이 찾아 반려한 상품을, 모델이 아무것도 읽지 못한 통과가 되살리면 안 된다",
        )
        assertFalse(listedByBuyer())
        assertEquals(
            listOf(ReviewQueueStatus.PENDING_REVIEW),
            queueStatuses(),
            "그 통과로 행까지 닫으면 관리자가 오탐을 풀 기회도 함께 사라진다",
        )
    }

    @Test
    fun `관점 행이 아예 없는 통과도 AI 반려를 풀지 않는다`() {
        applyVerdict(givenQueued(hash = "a".repeat(64)), InspectionVerdict.FLAG)

        applyVerdict(givenQueued(hash = "b".repeat(64)), InspectionVerdict.PASS, perspectives = emptyList())

        assertEquals(InspectionStatus.REJECTED, storedStatus(), "관점 행이 비면 모델이 무엇을 봤는지 알 길이 없다")
        assertEquals(listOf(ReviewQueueStatus.PENDING_REVIEW), queueStatuses())
    }

    @Test
    fun `선별이 건너뛴 통과는 AI 반려를 풀어 노출을 되돌린다`() {
        applyVerdict(givenQueued(hash = "a".repeat(64)), InspectionVerdict.FLAG)

        applyVerdict(
            givenQueued(hash = "b".repeat(64)),
            InspectionVerdict.PASS,
            perspectives =
                listOf(
                    PerspectiveResult(InspectionPerspective.TRIAGE, ContractOutcome.CLEAR),
                    PerspectiveResult(InspectionPerspective.DISPLAY_ADS, ContractOutcome.UNCHECKED, TRIAGE_CLEAR),
                    PerspectiveResult(InspectionPerspective.CONDUCT_TRADE, ContractOutcome.UNCHECKED, TRIAGE_CLEAR),
                ),
        )

        assertEquals(
            InspectionStatus.APPROVED,
            storedStatus(),
            "선별이 일부러 건너뛴 통과까지 막으면 선별 계층의 비용 절감이 사라진다",
        )
        assertTrue(listedByBuyer())
        assertEquals(listOf(ReviewQueueStatus.SUPERSEDED), queueStatuses())
    }

    @Test
    fun `증거 없는 통과는 열린 행의 대기 시각을 밀지 않는다`() {
        applyVerdict(givenQueued(hash = "a".repeat(64)), InspectionVerdict.FLAG)
        val opened = openedAt()

        applyVerdict(
            givenQueued(hash = "b".repeat(64)),
            InspectionVerdict.PASS,
            perspectives =
                listOf(PerspectiveResult(InspectionPerspective.ALL, ContractOutcome.UNCHECKED, "BUDGET_EXHAUSTED")),
        )

        assertEquals(opened, openedAt(), "모델이 읽지 않은 판정으로 대기 시각을 밀면 적체가 얼마나 묵었는지가 리셋된다")
    }

    @Test
    fun `옛 내용의 판정이 늦게 오면 상품과 큐에 반영하지 않는다`() {
        val older = givenQueued(hash = "a".repeat(64))
        val newer = givenQueued(hash = "b".repeat(64))
        applyVerdict(newer, InspectionVerdict.FLAG)

        applyVerdict(older, InspectionVerdict.PASS)

        assertEquals(InspectionStatus.REJECTED, storedStatus(), "고치기 전 문구의 통과가 고친 뒤 문구의 위반을 덮으면 안 된다")
        assertEquals(listOf(ReviewQueueStatus.PENDING_REVIEW), queueStatuses())
    }

    /**
     * 최신 검수 확인과 상태 전이 사이에 셀러 수정이 끼어들 수 있다.
     *
     * 최신 확인이 상품 행 잠금보다 먼저 돌아서, 확인을 지난 뒤 잠금을 기다리는 사이에 셀러
     * 수정과 새 검수가 커밋된다. 검수 행의 낙관적 락은 서로 다른 검수 행 사이를 지키지 않고,
     * 상품은 잠금을 받은 뒤에 읽으므로 상품 쪽 버전도 이것을 잡지 못한다.
     *
     * 여기서는 셀러가 상품 행을 쥔 채 판정 적용이 그 잠금을 기다리게 만든 뒤, 기다리는 동안
     * 새 검수를 커밋한다. 잠근 뒤에 최신 검수를 다시 보지 않으면 옛 내용의 통과가 새 내용의
     * 상품을 노출시킨다.
     */
    @Test
    fun `최신 확인 직후 셀러가 고쳐도 옛 판정이 새 내용을 승인하지 않는다`() {
        applyVerdict(givenQueued(hash = "a".repeat(64)), InspectionVerdict.FLAG)
        val stale = givenQueued(hash = "b".repeat(64))
        val productLocked = CountDownLatch(1)
        val verdictStarted = CountDownLatch(1)
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
                        // 판정 적용이 최신 확인을 지나 상품 행 잠금을 기다리기 시작할 틈을 준다.
                        // 그쪽이 막혀 있는 동안에는 래치를 내려 줄 수 없어 시간으로 센다.
                        assertTrue(verdictStarted.await(5, TimeUnit.SECONDS), "판정 적용이 시작하지 못했다")
                        Thread.sleep(LOCK_REACH_MILLIS)
                        givenQueued(hash = "c".repeat(64))
                    }
                }.onFailure(sellerFailure::set)
            }
        seller.start()
        assertTrue(productLocked.await(5, TimeUnit.SECONDS), "셀러가 상품 행을 잠그지 못했다")

        verdictStarted.countDown()
        applyVerdict(stale, InspectionVerdict.PASS)

        seller.join()
        assertNull(sellerFailure.get())
        assertEquals(
            InspectionStatus.REJECTED,
            storedStatus(),
            "잠금을 기다리는 사이에 셀러가 고쳤다. 옛 내용의 통과로 새 내용을 노출하면 안 된다",
        )
        assertEquals(
            listOf(ReviewQueueStatus.PENDING_REVIEW),
            queueStatuses(),
            "관리자가 볼 행까지 닫히면 새 내용을 아무도 보지 않는다",
        )
    }

    private fun listedByBuyer(): Boolean = productUseCase.getProducts(ProductSearchQuery(), null).items.any { it.productId == publicId }

    private fun storedStatus(): InspectionStatus =
        tx.execute {
            em
                .createQuery("select p.inspectionStatus from Product p where p.id = :id", InspectionStatus::class.java)
                .setParameter("id", productId)
                .singleResult
        }!!

    private fun applyVerdict(
        inspection: ContentInspection,
        verdict: InspectionVerdict,
        perspectives: List<PerspectiveResult> = defaultPerspectives(verdict),
    ) = inspectionUseCase.applyVerdict(ApplyVerdictCommand(verdictFor(inspection, verdict, perspectives)))

    /**
     * 판정과 어긋나지 않는 관점 행을 만든다.
     *
     * 통과 판정에 위반 관점을 실으면 메시지 자체가 부정합해져서, 증거를 보는 분기가
     * 우연히 통과한다. 그래서 판정마다 그 판정을 뒷받침하는 결과를 기본값으로 둔다.
     */
    private fun defaultPerspectives(verdict: InspectionVerdict): List<PerspectiveResult> =
        when (verdict) {
            InspectionVerdict.FLAG -> {
                listOf(PerspectiveResult(InspectionPerspective.DISPLAY_ADS, ContractOutcome.VIOLATION))
            }

            InspectionVerdict.PASS -> {
                listOf(PerspectiveResult(InspectionPerspective.DISPLAY_ADS, ContractOutcome.CLEAR))
            }

            InspectionVerdict.FAILED -> {
                listOf(PerspectiveResult(InspectionPerspective.DISPLAY_ADS, ContractOutcome.UNCHECKED, "CALL_FAILED"))
            }
        }

    private fun queueStatuses(): List<ReviewQueueStatus> =
        tx.execute {
            em
                .createQuery(
                    "select q.status from ProductReviewQueueItem q where q.targetId = :id order by q.id",
                    ReviewQueueStatus::class.java,
                ).setParameter("id", productId)
                .resultList
        }!!

    /** 열린 검토 행이 언제 열렸는지. 갱신되면 적체 나이가 리셋된다 */
    private fun openedAt(): LocalDateTime =
        tx.execute {
            em
                .createQuery(
                    "select q.blindedAt from ProductReviewQueueItem q where q.targetId = :id and q.status = :status",
                    LocalDateTime::class.java,
                ).setParameter("id", productId)
                .setParameter("status", ReviewQueueStatus.PENDING_REVIEW)
                .singleResult
        }!!

    private fun changeStatus(status: InspectionStatus) {
        tx.execute {
            em
                .createQuery("update Product p set p.inspectionStatus = :status where p.id = :id")
                .setParameter("status", status)
                .setParameter("id", productId)
                .executeUpdate()
        }
    }

    /** 관리자가 결정하고 닫은 행을 순서대로 쌓는다 */
    private fun givenAdminDecisions(vararg decisions: ReviewQueueStatus) {
        tx.execute {
            decisions.forEach { decision ->
                val item = ProductReviewQueueItem.openForProduct(productId, sellerUserId, "연어 건사료 2kg", LocalDateTime.now())
                val now = LocalDateTime.now()
                if (decision == ReviewQueueStatus.REJECTED) item.reject("치료 효능 표현을 빼 주세요.", now) else item.approve(now)
                em.persist(item)
            }
        }
    }

    private fun givenQueued(hash: String = "c".repeat(64)): ContentInspection =
        tx.execute {
            inspections.save(
                ContentInspection.queue(
                    targetType = InspectionTargetType.PRODUCT,
                    targetRef = productId,
                    contentHash = hash,
                    policyVersion = POLICY_VERSION,
                    targetVersionNo = 1,
                ),
            )
        }!!

    /** 요청 시각을 과거로 밀어 기한 경과를 만든다. 테스트가 10분을 기다릴 수는 없다 */
    private fun backdateRequestedAt(
        inspectionId: Long,
        minutes: Long,
    ) {
        tx.execute {
            em
                .createNativeQuery(
                    "update content_inspections set requested_at = DATE_SUB(NOW(6), INTERVAL :m MINUTE) where id = :id",
                ).setParameter("m", minutes)
                .setParameter("id", inspectionId)
                .executeUpdate()
        }
    }

    private fun verdictFor(
        inspection: ContentInspection,
        verdict: InspectionVerdict,
        perspectives: List<PerspectiveResult>,
    ): ContentVerdictMessage =
        ContentVerdictMessage(
            inspectionId = inspection.id,
            targetType = ContractTargetType.PRODUCT,
            targetRef = inspection.targetRef,
            contentHash = inspection.contentHash,
            verdict = verdict,
            perspectives = perspectives,
            violations =
                if (verdict == InspectionVerdict.FLAG) {
                    listOf(
                        Violation(
                            category = ViolationCategory.MEDICAL_CLAIM,
                            perspective = InspectionPerspective.DISPLAY_ADS,
                            spanStart = 0,
                            spanEnd = 10,
                            quote = "관절염 치료에 효과",
                            suggestion = "관절 건강에 도움을 줄 수 있는",
                            confidence = null,
                        ),
                    )
                } else {
                    emptyList()
                },
            sellerNotice = if (verdict == InspectionVerdict.FLAG) "치료 효능 표현을 고쳐 주세요." else null,
            calls = emptyList(),
            promptVersion = "inspection-v1+$POLICY_VERSION",
            schemaVersion = ContentVerdictMessage.SCHEMA_VERSION,
            decidedAt = Instant.parse("2026-10-19T03:00:05Z"),
        )

    companion object {
        private const val POLICY_VERSION = "policy-v1"

        // 선별이 모델 호출을 일부러 건너뛸 때 쓰는 사유. AI 서버와 맞춰 둔 값이다
        private const val TRIAGE_CLEAR = "TRIAGE_CLEAR"

        /** 판정 적용이 상품 행 잠금까지 도달할 시간. 막혀 있는 쪽은 래치를 내려 줄 수 없다 */
        private const val LOCK_REACH_MILLIS = 1_500L
    }
}
