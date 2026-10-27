package com.aechak.api.inspection

import com.aechak.api.support.IntegrationTestBase
import com.aechak.application.inspection.usecase.InspectionUseCase
import com.aechak.application.inspection.usecase.command.ApplyVerdictCommand
import com.aechak.application.product.product.usecase.ProductUseCase
import com.aechak.application.product.product.usecase.query.ProductSearchQuery
import com.aechak.domain.inspection.ContentInspection
import com.aechak.domain.inspection.enums.InspectionTargetType
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
    fun `위반 판정이면 구매자 조회에서 사라진다`() {
        assertTrue(listedByBuyer(), "판정 전에는 보여야 한다 — 등록 직후 숨기는 정책이 아니다")
        val inspection = givenQueued()

        inspectionUseCase.applyVerdict(ApplyVerdictCommand(verdictFor(inspection, InspectionVerdict.FLAG)))

        assertEquals(InspectionStatus.REJECTED, storedStatus())
        assertFalse(listedByBuyer(), "위반 판정을 받은 상품이 목록에 남아 있으면 게이트가 장식이다")
    }

    @Test
    fun `통과 판정이면 노출이 되돌아온다`() {
        inspectionUseCase.applyVerdict(
            ApplyVerdictCommand(verdictFor(givenQueued(hash = "a".repeat(64)), InspectionVerdict.FLAG)),
        )
        assertFalse(listedByBuyer())

        inspectionUseCase.applyVerdict(
            ApplyVerdictCommand(verdictFor(givenQueued(hash = "b".repeat(64)), InspectionVerdict.PASS)),
        )

        assertEquals(InspectionStatus.APPROVED, storedStatus())
        assertTrue(listedByBuyer(), "되돌리는 경로가 없으면 한 번 걸린 상품을 셀러가 영구히 되살릴 수 없다")
    }

    @Test
    fun `판정 과정이 실패한 건은 노출을 막지 않는다`() {
        val inspection = givenQueued()

        inspectionUseCase.applyVerdict(ApplyVerdictCommand(verdictFor(inspection, InspectionVerdict.FAILED)))

        assertEquals(InspectionStatus.APPROVED, storedStatus())
        assertTrue(listedByBuyer(), "판정에 실패한 것을 위반으로 다루면 모델 장애가 곧 매출 장애가 된다")
    }

    @Test
    fun `기한이 지나 미판정으로 닫힌 건은 노출을 막지 않는다`() {
        val inspection = givenQueued()
        backdateRequestedAt(inspection.id, minutes = 60)

        assertEquals(1, inspectionUseCase.expireOverdue())

        assertEquals(InspectionStatus.APPROVED, storedStatus())
        assertTrue(listedByBuyer(), "아무도 보지 않은 상품을 막으면 외부 지연이 그대로 노출 중단이 된다")
    }

    private fun listedByBuyer(): Boolean = productUseCase.getProducts(ProductSearchQuery(), null).items.any { it.productId == publicId }

    private fun storedStatus(): InspectionStatus =
        tx.execute {
            em
                .createQuery("select p.inspectionStatus from Product p where p.id = :id", InspectionStatus::class.java)
                .setParameter("id", productId)
                .singleResult
        }!!

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
