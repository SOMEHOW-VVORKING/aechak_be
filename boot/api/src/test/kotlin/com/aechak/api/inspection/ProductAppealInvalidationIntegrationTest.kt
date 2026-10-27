package com.aechak.api.inspection

import com.aechak.api.support.IntegrationTestBase
import com.aechak.application.inspection.service.ProductReviewQueueService
import com.aechak.application.inspection.usecase.InspectionUseCase
import com.aechak.application.inspection.usecase.command.ApplyVerdictCommand
import com.aechak.application.product.product.usecase.SellerProductUseCase
import com.aechak.application.product.product.usecase.command.AppealProductInspectionCommand
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

    /**
     * AI가 반려한 상품의 이의는 **그때 열린 행에** 붙는다.
     *
     * 자격을 여는 신호가 둘(열린 행 / 관리자 반려)인데 AI 반려에는 관리자 결정 이력이 없다.
     * 그래서 이 경로가 열린 행 쪽으로 들어오는 것이 셀러의 유일한 탈출 경로이고, 상태를 손으로
     * 심지 않고 판정 수신 경로로 지어 그것을 확인한다. 행을 새로 열어 버리면 상품마다 열린 행이
     * 둘이 되어 관리자가 닫는 것이 하나뿐이고 남은 행이 셀러 정원을 영구히 먹는다.
     */
    @Test
    fun `AI가 반려한 상품은 열린 행에 바로 이의를 붙인다`() {
        applyVerdict(givenQueued(OLD_CONTENT), InspectionVerdict.FLAG)
        val opened = assertNotNull(findPending(), "위반 판정이 행을 열지 않으면 그 뒤가 전부 성립하지 않는다")

        appeal("치료 효능이 아니라 성분 설명입니다.")

        val row = assertNotNull(findPending())
        assertEquals(opened.id, row.id, "행을 새로 열면 상품마다 열린 행이 둘이 되어 하나가 영구히 남는다")
        assertNotNull(row.appealedAt)
        assertEquals(1, row.appealAttemptCount)
        assertEquals(opened.blindedAt, row.blindedAt, "접수가 정렬 키를 밀면 이의 폭주가 곧 큐 정렬 조작이 된다")
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

    /** 기한 만료는 상품도 큐도 건드리지 않는다. 이의를 비울 자리에 아예 닿지 않는 것이 지금의 근거다 */
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

    /**
     * 셀러가 내용을 고치고 **그 판정이 오기 전에** 이의를 누른 경우.
     *
     * 접수 사유는 지금 걸려 있는 것(고치기 전 내용)에 대고 쓴 문장이다. 그런데 지문을 "가장
     * 최근 검수"에서 읽으면 아직 판정도 안 난 고친 내용의 지문이 적히고, 몇 분 뒤 그 내용의
     * 판정이 오면 지문이 같아 이의가 그대로 유지된다 — 관리자는 "셀러는 A에 억울하다 했는데
     * 지금 걸린 것은 B"를 읽는다. 지문의 출처는 그 행을 지금 막고 있는 판정이어야 한다.
     */
    @Test
    fun `고친 내용의 판정이 오기 전에 접수한 이의는 그 판정에서 무효가 된다`() {
        applyVerdict(givenQueued(OLD_CONTENT), InspectionVerdict.FLAG)
        val edited = givenQueued(NEW_CONTENT)
        appeal("고치기 전 문구는 성분 설명이었습니다.")

        applyVerdict(edited, InspectionVerdict.FLAG)

        val row = assertNotNull(findPending())
        assertNull(row.appealedAt, "고치기 전 내용에 쓴 사유가 고친 내용의 판정에 그대로 붙으면 안 된다")
        assertTrue(row.appealCleared(), "무효가 됐다는 사실은 남아야 한다")
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

    /**
     * 접수는 **운영 진입점**으로 한다.
     *
     * 서비스를 테스트 트랜잭션에서 직접 부르면 그 트랜잭션의 격리 수준이 기본값이라,
     * 파사드가 선언한 READ COMMITTED가 한 번도 태워지지 않는다. 그 선언이 떠받치는 것이
     * 잠금 조회의 간극 회피와 잠금 뒤 비잠금 조회의 최신성 둘이라, 선언을 지우고도 이 수트가
     * 초록이면 수트가 아무것도 지키지 않는 것이다.
     */
    private fun appeal(reason: String) {
        sellerProductUseCase.appealMyProductInspection(
            AppealProductInspectionCommand(
                sellerId = sellerUserId,
                productPublicId = productPublicId,
                reason = reason,
            ),
        )
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
