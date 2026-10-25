package com.aechak.api.inspection

import com.aechak.api.support.IntegrationTestBase
import com.aechak.application.inspection.service.ProductReviewQueueService
import com.aechak.application.inspection.usecase.InspectionUseCase
import com.aechak.application.inspection.usecase.command.ApplyVerdictCommand
import com.aechak.domain.inspection.ContentInspection
import com.aechak.domain.inspection.ProductReviewQueueItem
import com.aechak.domain.inspection.enums.InspectionTargetType
import com.aechak.domain.inspection.enums.ReviewQueueStatus
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
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import com.aechak.message.inspection.InspectionTargetType as ContractTargetType
import com.aechak.message.inspection.PerspectiveOutcome as ContractOutcome

/**
 * [통합] 접수된 이의가 **언제 무효가 되는가**를 고정한다.
 *
 * 무효가 되는 조건은 하나뿐이어야 한다 — 셀러가 내용을 고쳐서 적어 둔 사유가 지금 걸린 것을
 * 가리키지 않게 된 경우다. 그러면 관리자가 "셀러는 A에 억울하다 했는데 지금 걸린 것은 B"를
 * 보지 않고, 셀러는 재접수가 열린다.
 *
 * 그 조건을 "열린 행에 새 판정이 왔는가"로 잡으면 내용이 그대로인 경우까지 함께 지운다.
 * 같은 내용에 판정이 또 오는 길은 **정책 버전이 올라 전수를 다시 보는 것**이다. 검수 요청은
 * (대상, 내용 지문, 정책 버전)으로 재사용을 가르므로 같은 내용·같은 정책에는 판정이 두 번
 * 오지 않지만, 정책이 올라가면 같은 내용으로 검수 행이 새로 생기고 판정도 새로 온다. 그 판정이
 * 또 걸리거나 기한을 넘기면 이 메서드로 들어오는데, 셀러가 한 일은 없다. 거기서 이의가 지워지면
 * 정책 버전 한 번 올리는 것이 열린 이의 전건을 지우는 일이 된다.
 *
 * 지울 때는 지웠다는 사실을 남긴다. 그러지 않으면 셀러 응답이 "이의가 없다"와 "이의가 무효가
 * 됐다"를 같은 모양(appealedAt=null)으로 내려보내 접수가 실패한 것과 구별되지 않는다.
 */
@TestPropertySource(properties = ["inspection.gate-enabled=true"])
class ProductAppealInvalidationIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var inspections: ContentInspectionRepository

    @Autowired
    private lateinit var inspectionUseCase: InspectionUseCase

    @Autowired
    private lateinit var reviewQueueService: ProductReviewQueueService

    private var sellerUserId = 0L
    private var productId = 0L

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
        }
    }

    @Test
    fun `정책 버전이 올라 같은 내용을 다시 봐도 접수된 이의는 남는다`() {
        applyVerdict(givenQueued(OLD_CONTENT), InspectionVerdict.FLAG)
        appeal("치료 효능이 아니라 성분 설명입니다.")

        applyVerdict(givenQueued(OLD_CONTENT, NEXT_POLICY), InspectionVerdict.FLAG)

        val row = assertNotNull(findPending(), "행은 갱신만 되고 열려 있어야 한다")
        assertNotNull(row.appealedAt, "내용이 그대로인데 이의를 지우면 셀러가 제출한 것을 아무도 못 본다")
        assertEquals("치료 효능이 아니라 성분 설명입니다.", row.appealReason)
    }

    @Test
    fun `재검수가 기한을 넘겨도 접수된 이의는 남는다`() {
        applyVerdict(givenQueued(OLD_CONTENT), InspectionVerdict.FLAG)
        appeal("성분 설명입니다.")

        val retry = givenQueued(OLD_CONTENT, NEXT_POLICY)
        backdateRequestedAt(retry.id, minutes = 60)
        assertEquals(1, inspectionUseCase.expireOverdue())

        assertNotNull(findPending()!!.appealedAt, "AI 장애가 셀러의 이의를 지우면 안 된다")
    }

    @Test
    fun `내용이 바뀌면 이의가 무효가 되고 무효가 됐다는 사실이 남는다`() {
        applyVerdict(givenQueued(OLD_CONTENT), InspectionVerdict.FLAG)
        appeal("성분 설명입니다.")

        applyVerdict(givenQueued(NEW_CONTENT), InspectionVerdict.FLAG)

        val row = assertNotNull(findPending())
        assertNull(row.appealedAt, "고친 뒤의 사유는 지금 걸린 것을 가리키지 않는다")
        assertNull(row.appealReason)
        assertTrue(row.appealCleared(), "무효가 됐다는 사실이 남지 않으면 접수 실패와 구별되지 않는다")
    }

    @Test
    fun `이의를 접수한 적이 없는 행은 무효 표식도 없다`() {
        applyVerdict(givenQueued(OLD_CONTENT), InspectionVerdict.FLAG)

        applyVerdict(givenQueued(NEW_CONTENT), InspectionVerdict.FLAG)

        assertFalse(findPending()!!.appealCleared(), "접수한 적이 없는데 무효 표식이 서면 화면이 거짓을 그린다")
    }

    @Test
    fun `무효가 된 뒤 다시 접수하면 표식이 걷힌다`() {
        applyVerdict(givenQueued(OLD_CONTENT), InspectionVerdict.FLAG)
        appeal("성분 설명입니다.")
        applyVerdict(givenQueued(NEW_CONTENT), InspectionVerdict.FLAG)

        appeal("고친 문구도 성분 설명입니다.")

        val row = assertNotNull(findPending())
        assertNotNull(row.appealedAt)
        assertFalse(row.appealCleared(), "지금 걸린 이의가 있는데 무효 표식이 함께 서면 안 된다")
    }

    private fun appeal(reason: String) {
        tx.execute {
            val target = reviewQueueService.lockProductForAppeal(productId)
            reviewQueueService.appealBySeller(productId, target, reason, LocalDateTime.now())
        }
    }

    private fun findPending(): ProductReviewQueueItem? =
        tx.execute {
            val rows =
                em
                    .createQuery(
                        "select i from ProductReviewQueueItem i where i.targetId = :id and i.status = :st",
                        ProductReviewQueueItem::class.java,
                    ).setParameter("id", productId)
                    .setParameter("st", ReviewQueueStatus.PENDING_REVIEW)
                    .resultList
            assertTrue(rows.size <= 1, "상품마다 열린 행은 하나여야 한다. 실제=${rows.size}")
            rows.firstOrNull()
        }

    private fun applyVerdict(
        inspection: ContentInspection,
        verdict: InspectionVerdict,
    ) = inspectionUseCase.applyVerdict(ApplyVerdictCommand(verdictFor(inspection, verdict)))

    private fun givenQueued(
        hash: String,
        policyVersion: String = POLICY_VERSION,
    ): ContentInspection =
        tx.execute {
            inspections.save(
                ContentInspection.queue(
                    targetType = InspectionTargetType.PRODUCT,
                    targetRef = productId,
                    contentHash = hash,
                    policyVersion = policyVersion,
                    targetVersionNo = 1,
                ),
            )
        }!!

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
                listOf(
                    PerspectiveResult(
                        InspectionPerspective.DISPLAY_ADS,
                        if (verdict == InspectionVerdict.FLAG) ContractOutcome.VIOLATION else ContractOutcome.CLEAR,
                    ),
                ),
            violations = emptyList(),
            sellerNotice = null,
            calls = emptyList(),
            promptVersion = "inspection-v1+$POLICY_VERSION",
            schemaVersion = ContentVerdictMessage.SCHEMA_VERSION,
            decidedAt = Instant.parse("2026-10-19T03:00:05Z"),
        )

    companion object {
        private const val POLICY_VERSION = "policy-v1"
        private const val NEXT_POLICY = "policy-v2"
        private val OLD_CONTENT = "a".repeat(64)
        private val NEW_CONTENT = "b".repeat(64)
    }
}
