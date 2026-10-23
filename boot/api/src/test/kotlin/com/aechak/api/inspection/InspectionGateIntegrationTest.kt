package com.aechak.api.inspection

import com.aechak.api.support.IntegrationTestBase
import com.aechak.application.inspection.usecase.InspectionUseCase
import com.aechak.application.inspection.usecase.command.ApplyVerdictCommand
import com.aechak.application.product.product.usecase.ProductUseCase
import com.aechak.application.product.product.usecase.query.ProductSearchQuery
import com.aechak.domain.inspection.ContentInspection
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
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
 * 반려는 관리자만 만든다. AI가 문제를 찾았거나 판단하지 못한 상품은 관리자 검토 큐에 오르고,
 * 관리자가 반려한 상품은 고친 내용이 통과해도 관리자가 다시 본다.
 */
@TestPropertySource(properties = ["inspection.gate-enabled=true"])
class InspectionGateIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var inspections: ContentInspectionRepository

    @Autowired
    private lateinit var inspectionUseCase: InspectionUseCase

    @Autowired
    private lateinit var productUseCase: ProductUseCase

    private var productId = 0L
    private lateinit var publicId: String

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
    fun `위반 판정이면 구매자 조회에서 사라지고 관리자 검토 큐에 오른다`() {
        assertTrue(listedByBuyer(), "판정 전에는 보여야 한다. 이 상품은 게이트를 켜기 전에 등록된 것이다")
        val inspection = givenQueued()

        applyVerdict(inspection, InspectionVerdict.FLAG)

        assertEquals(InspectionStatus.PENDING, storedStatus(), "반려는 관리자만 만든다. AI는 확인 대기로만 보낸다")
        assertFalse(listedByBuyer(), "위반 판정을 받은 상품이 목록에 남아 있으면 게이트가 장식이다")
        assertEquals(listOf(ReviewQueueStatus.PENDING_REVIEW), queueStatuses(), "관리자가 볼 행이 하나 열려야 한다")
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
    fun `판정 과정이 실패한 건은 노출을 막지 않고 관리자 검토 큐에 오른다`() {
        applyVerdict(givenQueued(), InspectionVerdict.FAILED)

        assertEquals(InspectionStatus.APPROVED, storedStatus())
        assertTrue(listedByBuyer(), "판정에 실패한 것을 위반으로 다루면 모델 장애가 곧 매출 장애가 된다")
        assertEquals(listOf(ReviewQueueStatus.PENDING_REVIEW), queueStatuses(), "아무도 보지 않은 상품은 사람이 봐야 한다")
    }

    @Test
    fun `기한이 지나 미판정으로 닫힌 건은 노출을 막지 않고 관리자 검토 큐에 오른다`() {
        val inspection = givenQueued()
        backdateRequestedAt(inspection.id, minutes = 60)

        assertEquals(1, inspectionUseCase.expireOverdue())

        assertEquals(InspectionStatus.APPROVED, storedStatus())
        assertTrue(listedByBuyer(), "아무도 보지 않은 상품을 막으면 외부 지연이 그대로 노출 중단이 된다")
        assertEquals(
            listOf(ReviewQueueStatus.PENDING_REVIEW),
            queueStatuses(),
            "기한 정리가 큐에 올리지 않으면 등록 직후 숨긴 상품이 영영 PENDING에 남는다",
        )
    }

    @Test
    fun `등록 직후 숨긴 상품은 통과 판정을 받으면 관리자 확인 없이 노출된다`() {
        changeStatus(InspectionStatus.PENDING)

        applyVerdict(givenQueued(), InspectionVerdict.PASS)

        assertEquals(InspectionStatus.APPROVED, storedStatus())
        assertTrue(listedByBuyer())
        assertEquals(emptyList<ReviewQueueStatus>(), queueStatuses(), "통과한 신규 상품까지 관리자가 보면 큐가 막힌다")
    }

    @Test
    fun `관리자가 반려한 상품은 고친 내용이 통과해도 관리자가 다시 본다`() {
        changeStatus(InspectionStatus.REJECTED)

        applyVerdict(givenQueued(), InspectionVerdict.PASS)

        assertEquals(InspectionStatus.REJECTED, storedStatus(), "사람이 내린 반려를 AI 통과가 풀면 안 된다")
        assertEquals(listOf(ReviewQueueStatus.PENDING_REVIEW), queueStatuses())
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
    fun `옛 내용의 판정이 늦게 오면 상품과 큐에 반영하지 않는다`() {
        val older = givenQueued(hash = "a".repeat(64))
        val newer = givenQueued(hash = "b".repeat(64))
        applyVerdict(newer, InspectionVerdict.FLAG)

        applyVerdict(older, InspectionVerdict.PASS)

        assertEquals(InspectionStatus.PENDING, storedStatus(), "고치기 전 문구의 통과가 고친 뒤 문구의 위반을 덮으면 안 된다")
        assertEquals(listOf(ReviewQueueStatus.PENDING_REVIEW), queueStatuses())
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
    ) = inspectionUseCase.applyVerdict(ApplyVerdictCommand(verdictFor(inspection, verdict)))

    private fun queueStatuses(): List<ReviewQueueStatus> =
        tx.execute {
            em
                .createQuery(
                    "select q.status from ProductReviewQueueItem q where q.targetId = :id order by q.id",
                    ReviewQueueStatus::class.java,
                ).setParameter("id", productId)
                .resultList
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
    ): ContentVerdictMessage =
        ContentVerdictMessage(
            inspectionId = inspection.id,
            targetType = ContractTargetType.PRODUCT,
            targetRef = inspection.targetRef,
            contentHash = inspection.contentHash,
            verdict = verdict,
            perspectives =
                listOf(PerspectiveResult(InspectionPerspective.DISPLAY_ADS, ContractOutcome.VIOLATION)),
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
    }
}
