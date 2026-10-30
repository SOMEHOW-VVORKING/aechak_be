package com.aechak.api.inspection

import com.aechak.api.support.IntegrationTestBase
import com.aechak.application.inspection.usecase.InspectionMetricsUseCase
import com.aechak.application.inspection.usecase.InspectionUseCase
import com.aechak.application.inspection.usecase.command.ApplyVerdictCommand
import com.aechak.domain.inspection.ContentInspection
import com.aechak.domain.inspection.enums.InspectionTargetType
import com.aechak.domain.inspection.repository.ContentInspectionRepository
import com.aechak.domain.product.category.Category
import com.aechak.domain.product.product.Product
import com.aechak.domain.product.stats.ProductStats
import com.aechak.domain.seller.seller.Seller
import com.aechak.message.inspection.ContentVerdictMessage
import com.aechak.message.inspection.InspectionPerspective
import com.aechak.message.inspection.InspectionVerdict
import com.aechak.message.inspection.PerspectiveResult
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource
import java.time.Instant
import kotlin.test.assertEquals
import com.aechak.message.inspection.InspectionTargetType as ContractTargetType
import com.aechak.message.inspection.PerspectiveOutcome as ContractOutcome

/**
 * [통합] 적체 버킷이 **판정 수신 경로가 실제로 만든 행**을 센다.
 *
 * 적체 수트 대부분은 검토 행을 직접 심는다. 그것으로는 "그 상태가 실제로 생기는가"를 보지
 * 못하고, 등재 규칙이 바뀌면 손으로 심은 상태만 남아 통과하는 테스트가 된다. 이 기능에서 전에
 * 겪은 모양이라 지표 쪽에도 생산 경로로 짓는 단언을 하나 박아 둔다.
 *
 * 버킷의 축은 "그 행을 연 판정"이 아니라 그 상품의 가장 최근 판정이다. AI 반려 직후에는 둘이
 * 같은 검수라서 여기서 보는 것이 그 교집합이다.
 */
@TestPropertySource(
    properties = [
        "inspection.gate-enabled=true",
        // 캐시를 끈다. 켜 두면 앞 테스트의 스냅샷을 그대로 읽는다.
        "inspection.metrics.snapshot-cache-seconds=0",
    ],
)
class InspectionBacklogBucketIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var inspections: ContentInspectionRepository

    @Autowired
    private lateinit var inspectionUseCase: InspectionUseCase

    @Autowired
    private lateinit var metricsUseCase: InspectionMetricsUseCase

    private var productId = 0L

    @BeforeEach
    fun seed() {
        val sellerUserId = createActiveUser()
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
                    description = "관절염 치료에 효과",
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
        }
    }

    @Test
    fun `적체는 AI 반려로 열린 행을 위반 버킷으로 센다`() {
        applyVerdict(givenQueued("a".repeat(64)), InspectionVerdict.FLAG)

        val backlog = metricsUseCase.snapshot().backlog()

        assertEquals(1, backlog.openCount(), "위반 판정이 행을 열지 않으면 적체 지표가 빈 큐를 보고한다")
        assertEquals(
            mapOf("FLAG" to 1L),
            backlog.openByLatestJudgment(),
            "아무도 다시 보지 않은 AI 반려가 위반 버킷에 들어와야 운영이 무엇을 먼저 볼지 가린다",
        )
    }

    /**
     * 모델이 읽지 않은 통과는 열린 행을 닫지 않고, 그 판정이 최신이 되어 통과 버킷에 섞인다.
     *
     * 적체 버킷 하나에 "사람이 볼 차례"와 "판정 계약이 갈라졌다"가 함께 담긴다는 사실이고,
     * 가르려면 지금 그 행을 막고 있는 판정을 행에 적어야 해서 쿼리로는 가르지 않는다. 대신
     * 함께 읽을 신호가 누락 수치다 — 통과 버킷이 늘고 그 값도 같이 늘면 드리프트 쪽이다.
     */
    @Test
    fun `증거 없는 통과가 오면 열린 행이 통과 버킷으로 세어지고 누락 수치가 함께 오른다`() {
        applyVerdict(givenQueued("a".repeat(64)), InspectionVerdict.FLAG)

        applyVerdict(
            givenQueued("b".repeat(64)),
            InspectionVerdict.PASS,
            perspectives = listOf(PerspectiveResult(InspectionPerspective.DISPLAY_ADS, ContractOutcome.UNCHECKED, "RATE_LIMITED")),
        )

        val snapshot = metricsUseCase.snapshot()
        assertEquals(1, snapshot.backlog().openCount(), "모델이 읽지 않은 통과가 행을 닫으면 아무도 그 상품을 보지 않는다")
        assertEquals(mapOf("PASS" to 1L), snapshot.backlog().openByLatestJudgment())
        assertEquals(1, snapshot.coverage().modelUnverifiedCount(), "섞인 쪽을 가려 읽을 신호가 함께 올라와야 한다")
    }

    private fun applyVerdict(
        inspection: ContentInspection,
        verdict: InspectionVerdict,
        perspectives: List<PerspectiveResult> = defaultPerspectives(verdict),
    ) = inspectionUseCase.applyVerdict(ApplyVerdictCommand(verdictFor(inspection, verdict, perspectives)))

    /** 판정을 뒷받침하는 관점 결과. 어긋나게 실으면 증거를 보는 분기가 우연히 통과한다 */
    private fun defaultPerspectives(verdict: InspectionVerdict): List<PerspectiveResult> =
        listOf(
            PerspectiveResult(
                InspectionPerspective.DISPLAY_ADS,
                if (verdict == InspectionVerdict.FLAG) ContractOutcome.VIOLATION else ContractOutcome.CLEAR,
            ),
        )

    private fun givenQueued(hash: String): ContentInspection =
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
            violations = emptyList(),
            sellerNotice = null,
            calls = emptyList(),
            promptVersion = "inspection-v1+$POLICY_VERSION",
            schemaVersion = ContentVerdictMessage.SCHEMA_VERSION,
            decidedAt = Instant.parse("2026-10-19T03:00:05Z"),
        )

    private companion object {
        const val POLICY_VERSION = "policy-v1"
    }
}
