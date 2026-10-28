package com.aechak.api.inspection

import com.aechak.api.support.IntegrationTestBase
import com.aechak.application.inspection.service.ProductReviewQueueService
import com.aechak.application.inspection.usecase.InspectionUseCase
import com.aechak.application.inspection.usecase.command.ApplyVerdictCommand
import com.aechak.application.product.product.usecase.SellerProductUseCase
import com.aechak.application.product.product.usecase.result.InspectionProgressStep
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
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource
import java.time.Instant
import java.time.LocalDateTime
import kotlin.test.assertEquals
import com.aechak.message.inspection.InspectionTargetType as ContractTargetType
import com.aechak.message.inspection.PerspectiveOutcome as ContractOutcome

/**
 * [통합] 노출 게이트 스위치가 **꺼진** 환경을 고정한다. 배포된 모든 환경의 값이 이것이다.
 *
 * 이 수트가 있는 이유는 스위치 하나가 서로 다른 둘을 함께 끄고 있었기 때문이다. 스위치의 뜻은
 * "구매자 화면에 반영"인데 구현이 "관리자 큐에 등재"까지 함께 껐다. 그래서 꺼진 환경에서는
 * 열린 검토 행이 한 건도 생기지 않고, 관리자가 반려할 행도 없어 어떤 상품도 반려에 닿지 못하고,
 * 접수 자격의 신호(열린 행 / 관리자 반려) 둘이 영구히 거짓이라 셀러 이의가 전건 409로 떨어졌다.
 *
 * 그래서 가르는 두 축을 각각 단언한다. **노출 전이는 스위치를 본다** — 꺼져 있으면 AI 판정이
 * 구매자에게 보이는 상태를 바꾸지 않는다. **큐 등재와 사람 결정은 스위치를 보지 않는다** —
 * 판정을 쌓아 품질을 보는 단계에서도 사람이 볼 목록은 차야 하고, 사람이 내린 결정은 반영된다.
 *
 * 꺼진 환경을 따로 보는 이유가 하나 더 있다. 스위치가 꺼져 있으면 AI 위반 판정이 상품을 반려하지
 * 못해 "이전 상태가 반려"에 기대는 방어가 전부 무효가 된다. 그 자리에서 무엇이 여전히 서는지를
 * 여기서 고정한다.
 */
@TestPropertySource(properties = ["inspection.gate-enabled=false"])
class InspectionGateDisabledIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var inspections: ContentInspectionRepository

    @Autowired
    private lateinit var inspectionUseCase: InspectionUseCase

    @Autowired
    private lateinit var reviewQueueService: ProductReviewQueueService

    @Autowired
    private lateinit var sellerProductUseCase: SellerProductUseCase

    private var productId = 0L
    private var sellerUserId = 0L
    private lateinit var productPublicId: String

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
            productPublicId = product.publicId
        }
    }

    /**
     * 꺼진 환경에서도 AI가 찾은 위반은 셀러 화면에서 반려로 보인다.
     *
     * 셀러 진행 단계를 상품의 노출 상태로 가르면 이 환경에서 위반 판정이 "승인"으로 보인다 —
     * 스위치가 전이를 끄기 때문이다. 배포된 전 환경의 값이 꺼짐이라 그것이 곧 현재 동작이 된다.
     * 그러면 셀러는 승인 화면을 보다가 이의를 넣는 순간 관리자 검토 중으로 바뀌는 것을 본다.
     * 열린 행과 그 행을 연 판정으로 가르면 답이 스위치와 무관해진다.
     */
    @Test
    fun `스위치가 꺼져도 위반으로 열린 행은 셀러에게 반려로 보인다`() {
        applyVerdict(givenQueued(), InspectionVerdict.FLAG)

        val seen = sellerProductUseCase.getMyProductInspection(sellerUserId, productPublicId)

        assertEquals(
            InspectionProgressStep.REJECTED,
            seen.progressStep,
            "AI가 위반을 찾았는데 셀러에게 승인으로 보이면 고칠 이유가 없는 화면이 된다",
        )
        assertEquals(InspectionStatus.APPROVED, seen.inspectionStatus, "노출 전이만 스위치를 본다")
    }

    @Test
    fun `스위치가 꺼져도 위반 판정은 관리자 검토 큐에 오른다`() {
        applyVerdict(givenQueued(), InspectionVerdict.FLAG)

        assertEquals(
            listOf(ReviewQueueStatus.PENDING_REVIEW),
            queueStatuses(),
            "큐 등재가 스위치에 묶이면 배포된 환경에 사람이 볼 목록이 영구히 비고 이의 자격도 열리지 않는다",
        )
        assertEquals(InspectionStatus.APPROVED, storedStatus(), "스위치가 꺼져 있으면 노출은 그대로다")
    }

    @Test
    fun `스위치가 꺼져도 관리자가 반려해 이의 자격을 열 수 있다`() {
        applyVerdict(givenQueued(), InspectionVerdict.FLAG)

        tx.execute { reviewQueueService.rejectByAdmin(productId, "효능 표현을 빼 주세요.", LocalDateTime.now()) }

        assertEquals(InspectionStatus.REJECTED, storedStatus(), "사람이 내린 결정은 스위치와 무관하게 반영된다")
        assertEquals(listOf(ReviewQueueStatus.REJECTED), queueStatuses())
    }

    @Test
    fun `스위치가 꺼져도 고친 내용이 통과하면 열린 행이 닫힌다`() {
        applyVerdict(givenQueued(hash = "a".repeat(64)), InspectionVerdict.FLAG)

        applyVerdict(givenQueued(hash = "b".repeat(64)), InspectionVerdict.PASS)

        assertEquals(
            listOf(ReviewQueueStatus.SUPERSEDED),
            queueStatuses(),
            "등재만 하고 닫지 않으면 큐가 한 방향으로만 차서 사람이 끝난 일을 다시 본다",
        )
    }

    /**
     * 꺼진 환경에서도 증거 없는 통과는 열린 행을 닫지 않는다.
     *
     * 증거 없는 통과를 "되살림 전이를 막는다"로 푸는 쪽을 버린 이유가 여기다. 꺼진 환경에서는
     * 위반 판정이 상품을 반려하지 못해 "이전 상태가 반려"가 성립하지 않고, 그 상태에서 모델이
     * 아무것도 읽지 못한 통과가 사람이 볼 행을 조용히 닫는다. 배포된 전 환경이 꺼짐이라
     * 그 구멍이 곧 현재가 된다. 게이트 분기에서 빼면 꺼진 환경에서도 행이 살아 있다.
     */
    @Test
    fun `스위치가 꺼져도 증거 없는 통과는 열린 행을 닫지 않는다`() {
        applyVerdict(givenQueued(hash = "a".repeat(64)), InspectionVerdict.FLAG)

        applyVerdict(
            givenQueued(hash = "b".repeat(64)),
            InspectionVerdict.PASS,
            perspectives = listOf(PerspectiveResult(InspectionPerspective.DISPLAY_ADS, ContractOutcome.UNCHECKED, "RATE_LIMITED")),
        )

        assertEquals(
            listOf(ReviewQueueStatus.PENDING_REVIEW),
            queueStatuses(),
            "모델이 아무것도 읽지 못한 통과가 사람이 볼 행을 닫으면 아무도 그 상품을 보지 않는다",
        )
    }

    private fun storedStatus(): InspectionStatus =
        tx.execute {
            em
                .createQuery("select p.inspectionStatus from Product p where p.id = :id", InspectionStatus::class.java)
                .setParameter("id", productId)
                .singleResult
        }!!

    private fun queueStatuses(): List<ReviewQueueStatus> =
        tx.execute {
            em
                .createQuery(
                    "select q.status from ProductReviewQueueItem q where q.targetId = :id order by q.id",
                    ReviewQueueStatus::class.java,
                ).setParameter("id", productId)
                .resultList
        }!!

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

    companion object {
        private const val POLICY_VERSION = "policy-v1"
    }
}
