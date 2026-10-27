package com.aechak.api.inspection

import com.aechak.api.support.IntegrationTestBase
import com.aechak.application.inspection.service.ProductReviewQueueService
import com.aechak.application.inspection.usecase.InspectionUseCase
import com.aechak.application.inspection.usecase.command.ApplyVerdictCommand
import com.aechak.application.product.product.usecase.SellerProductUseCase
import com.aechak.application.product.product.usecase.result.InspectionProgressStep
import com.aechak.application.product.product.usecase.result.SellerProductInspectionResult
import com.aechak.domain.inspection.ContentInspection
import com.aechak.domain.inspection.enums.InspectionJudgment
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
import com.aechak.message.inspection.Violation
import com.aechak.message.inspection.ViolationCategory
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource
import java.time.Instant
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNull
import com.aechak.message.inspection.InspectionTargetType as ContractTargetType
import com.aechak.message.inspection.PerspectiveOutcome as ContractOutcome

/**
 * [통합] 셀러·관리자 화면의 사유와 진행 단계가 **지금 그 검토 행을 막고 있는 판정**에서 나오는지 고정한다.
 *
 * 정본 설계에서 모델이 답을 못 준 판정 셋(판정 실패, 기한 만료, 모델이 읽은 적 없는 통과)은
 * 상품도 큐도 건드리지 않는다. 그런데 화면이 "가장 최근 검수"를 읽으면 그 판정들이 앞선 위반
 * 판정의 자리를 덮어, 반려는 그대로 남고 사유 셋만 비어 버린다. 셀러는 무엇을 고칠지 모르고
 * 관리자는 무엇을 풀지 모른다 — 정본에서 그 검토 행이 AI 반려를 푸는 유일한 자리라서 그 공백이
 * 곧 해제 불가다. 모델 장애 한 번이면 들어가는 상태다.
 *
 * 되찾는 열쇠는 행의 `blindedAt`이다. 그 값이 그 행을 열거나 다시 연 판정의 판정 시각이라서,
 * 그것으로 찾은 검수가 지금 막고 있는 판정이다. 그래서 이 수트는 상태를 손으로 심지 않고
 * **판정 수신 경로로만 짓는다** — 손으로 심은 행의 시각은 어떤 검수와도 맞지 않아 대체 조회로
 * 떨어지고, 그러면 연결이 실제로 서는지 확인할 수 없다.
 */
@TestPropertySource(properties = ["inspection.gate-enabled=true"])
class InspectionBlockingReasonIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var inspections: ContentInspectionRepository

    @Autowired
    private lateinit var inspectionUseCase: InspectionUseCase

    @Autowired
    private lateinit var reviewQueueService: ProductReviewQueueService

    @Autowired
    private lateinit var sellerProductUseCase: SellerProductUseCase

    private var sellerUserId = 0L
    private var productId = 0L
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
            productPublicId = product.publicId
        }
    }

    @Test
    fun `판정이 실패해도 앞선 위반의 사유와 반려 단계가 남는다`() {
        flag(givenQueued(FIRST_CONTENT))

        applyVerdict(givenQueued(SECOND_CONTENT), InspectionVerdict.FAILED)

        val seen = inspection()
        assertEquals(InspectionJudgment.FAILED, seen.judgment, "진행 축은 최신 검수의 것이다")
        assertEquals("MEDICAL_CLAIM", seen.reasonCode, "모델이 답을 못 줬다고 앞선 위반의 사유가 사라지면 고칠 곳을 알 수 없다")
        assertEquals(QUOTE, seen.reasonSummary)
        assertEquals(NOTICE, seen.sellerNotice)
        assertEquals(InspectionProgressStep.REJECTED, seen.progressStep)
    }

    @Test
    fun `기한을 넘겨 닫혀도 앞선 위반의 사유가 남는다`() {
        flag(givenQueued(FIRST_CONTENT))
        val retry = givenQueued(SECOND_CONTENT)
        backdateRequestedAt(retry.id)

        assertEquals(1, inspectionUseCase.expireOverdue())

        val seen = inspection()
        assertEquals(InspectionJudgment.UNJUDGED, seen.judgment)
        assertEquals(QUOTE, seen.reasonSummary, "기한 만료가 사유를 덮으면 모델 장애가 곧 '고칠 수 없는 반려'가 된다")
        assertEquals(InspectionProgressStep.REJECTED, seen.progressStep)
    }

    /**
     * 모델이 읽은 적 없는 통과는 아무것도 등재·갱신하지 않는다. 화면도 그 사실을 따라야 한다.
     *
     * 게이트가 일부러 버린 판정이 셀러 계약에서는 그대로 이겨서, 아무것도 바뀌지 않았는데
     * 셀러에게 "관리자가 보고 있다"고 말하던 자리다.
     */
    @Test
    fun `증거 없는 통과는 사유를 지우지 않고 관리자 확인으로 밀지도 않는다`() {
        flag(givenQueued(FIRST_CONTENT))

        applyVerdict(
            givenQueued(SECOND_CONTENT),
            InspectionVerdict.PASS,
            perspectives = listOf(PerspectiveResult(InspectionPerspective.DISPLAY_ADS, ContractOutcome.UNCHECKED, "RATE_LIMITED")),
        )

        val seen = inspection()
        assertEquals(QUOTE, seen.reasonSummary, "게이트가 버린 판정이 화면에서는 이기고 있었다")
        assertEquals(
            InspectionProgressStep.REJECTED,
            seen.progressStep,
            "등재·갱신이 하나도 없었는데 관리자 확인으로 밀면 셀러가 기다리지 않을 일을 기다린다",
        )
    }

    /**
     * 게이트에 반영되지 않은 지연 판정은 화면의 사유도 바꾸지 않는다.
     *
     * 판정 적용은 판정을 **적은 뒤에** 최신 검수인지 본다. 그래서 늦게 도착한 위반 판정도 검수
     * 이력에는 남는다 — "가장 최근 위반 검수"로 사유를 고르면 그 반영되지 않은 판정을 집는다.
     */
    @Test
    fun `반영되지 않은 지연 위반 판정은 화면의 사유를 바꾸지 않는다`() {
        flag(givenQueued(FIRST_CONTENT))
        val late = givenQueued(SECOND_CONTENT)
        applyVerdict(givenQueued(THIRD_CONTENT), InspectionVerdict.FAILED)

        flag(late, category = ViolationCategory.EFFICACY_OVERCLAIM, quote = "최고의 사료", notice = "비교 표현을 고쳐 주세요.")

        val seen = inspection()
        assertEquals("MEDICAL_CLAIM", seen.reasonCode, "반영되지 않은 판정의 사유를 보여 주면 셀러가 엉뚱한 곳을 고친다")
        assertEquals(QUOTE, seen.reasonSummary)
    }

    @Test
    fun `관리자가 반려한 상품의 재검수 통과는 그 뒤 판정이 실패해도 관리자 확인으로 남는다`() {
        flag(givenQueued(FIRST_CONTENT))
        tx.execute { reviewQueueService.rejectByAdmin(productId, ADMIN_REASON, LocalDateTime.now()) }
        applyVerdict(givenQueued(SECOND_CONTENT), InspectionVerdict.PASS)

        applyVerdict(givenQueued(THIRD_CONTENT), InspectionVerdict.FAILED)

        val seen = inspection()
        assertEquals(
            InspectionProgressStep.ADMIN_REVIEWING,
            seen.progressStep,
            "새 검수 하나가 실패한 것만으로 관리자 확인 대기가 반려로 되돌아가면 셀러가 또 고치려 든다",
        )
        assertEquals(ADMIN_REASON, seen.rejectReason, "막고 있는 것은 사람이 내린 반려다")
        assertNull(seen.reasonSummary, "그 행을 갱신한 것은 통과 판정이라 AI가 지적한 문구가 없다")
    }

    @Test
    fun `고친 내용이 통과해 행이 닫히면 앞선 위반의 사유를 싣지 않는다`() {
        flag(givenQueued(FIRST_CONTENT))

        applyVerdict(givenQueued(SECOND_CONTENT), InspectionVerdict.PASS)

        val seen = inspection()
        assertEquals(InspectionProgressStep.APPROVED, seen.progressStep)
        assertNull(seen.reasonSummary, "막고 있는 것이 없는데 지난 위반 문구를 보여 주면 고친 셀러가 또 고치려 든다")
    }

    private fun inspection(): SellerProductInspectionResult = sellerProductUseCase.getMyProductInspection(sellerUserId, productPublicId)

    private fun flag(
        inspection: ContentInspection,
        category: ViolationCategory = ViolationCategory.MEDICAL_CLAIM,
        quote: String = QUOTE,
        notice: String = NOTICE,
    ) = inspectionUseCase.applyVerdict(
        ApplyVerdictCommand(
            verdictFor(
                inspection,
                InspectionVerdict.FLAG,
                listOf(PerspectiveResult(InspectionPerspective.DISPLAY_ADS, ContractOutcome.VIOLATION)),
            ).copy(
                violations =
                    listOf(
                        Violation(
                            category = category,
                            perspective = InspectionPerspective.DISPLAY_ADS,
                            spanStart = null,
                            spanEnd = null,
                            quote = quote,
                            suggestion = null,
                            confidence = null,
                        ),
                    ),
                sellerNotice = notice,
            ),
        ),
    )

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

    private fun backdateRequestedAt(inspectionId: Long) {
        tx.execute {
            em
                .createNativeQuery(
                    "update content_inspections set requested_at = DATE_SUB(NOW(6), INTERVAL 60 MINUTE) where id = :id",
                ).setParameter("id", inspectionId)
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
            violations = emptyList(),
            sellerNotice = null,
            calls = emptyList(),
            promptVersion = "inspection-v1+$POLICY_VERSION",
            schemaVersion = ContentVerdictMessage.SCHEMA_VERSION,
            decidedAt = Instant.parse("2026-10-19T03:00:05Z"),
        )

    companion object {
        private const val POLICY_VERSION = "policy-v1"
        private const val QUOTE = "관절염 치료에 효과"
        private const val NOTICE = "치료 효능 표현을 고쳐 주세요."
        private const val ADMIN_REASON = "효능 표현을 빼고 다시 등록해 주세요."
        private val FIRST_CONTENT = "a".repeat(64)
        private val SECOND_CONTENT = "b".repeat(64)
        private val THIRD_CONTENT = "c".repeat(64)
    }
}
