package com.aechak.seller.product

import com.aechak.api.support.IntegrationTestBase
import com.aechak.domain.inspection.ContentInspection
import com.aechak.domain.inspection.ProductReviewQueueItem
import com.aechak.domain.inspection.enums.InspectionJudgment
import com.aechak.domain.inspection.enums.InspectionTargetType
import com.aechak.domain.inspection.enums.ReviewQueueStatus
import com.aechak.domain.inspection.repository.ContentInspectionRepository
import com.aechak.domain.inspection.repository.ProductReviewQueueRepository
import com.aechak.domain.product.category.Category
import com.aechak.domain.product.product.Product
import com.aechak.domain.product.product.enums.InspectionStatus
import com.aechak.domain.seller.seller.Seller
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.web.FilterChainProxy
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * [통합] 셀러 이의 제기를 고정한다.
 *
 * 여기서 잡는 것은 셋이다. **접수가 노출을 바꾸지 않는다** — 바꾸면 이의 제기가 곧 노출 복구
 * 수단이 되어 셀러가 스스로 반려를 풀 수 있다. **폭주가 막힌다** — 관리자 큐를 셀러가 혼자
 * 채울 수 있으면 사람이 보는 큐가 쓸모없어진다. 그리고 **결정할 수 없게 된 이의는 정원을 비운다**
 * — 그러지 않으면 "접수 뒤 수정"을 상한만큼 반복한 셀러가 영구히 이의 불가가 되고, 차단의
 * 유일한 탈출 경로가 이의라 다른 차단 상품까지 함께 묶인다.
 *
 * 자격을 여는 신호는 판정값이 아니라 **열린 검토 행**이다. 상품의 PENDING은 "첫 판정 대기"와
 * "사람 확인 대기"를 겸하고, 둘을 가르는 것이 그 행이다. 판정값이나 상태만 보면 등록 직후
 * 모든 상품이 이의를 받는다.
 *
 * 노출 게이트 스위치를 켜지 않고 돈다(테스트 기본값은 꺼짐). 사람이 누르는 경로가 그 스위치에
 * 묶이면 스위치가 꺼진 환경에서 이의가 조용히 아무것도 하지 않는다.
 *
 * 상한을 2로 낮춰 돌린다. 기본값 3으로는 상한 테스트가 상품을 넷 심어야 해서 의도가 흐려진다.
 */
@TestPropertySource(
    properties = [
        "inspection.appeal.max-open-per-seller=2",
        "inspection.appeal.cooldown-hours=24",
    ],
)
class SellerProductInspectionAppealIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var context: WebApplicationContext

    @Autowired
    private lateinit var securityFilterChain: FilterChainProxy

    @Autowired
    private lateinit var inspections: ContentInspectionRepository

    @Autowired
    private lateinit var reviewQueue: ProductReviewQueueRepository

    private lateinit var mockMvc: MockMvc
    private var sellerUserId = 0L
    private lateinit var token: String
    private var productId = 0L
    private lateinit var publicId: String
    private var leafCategoryId = 0L

    @BeforeEach
    fun setUp() {
        mockMvc =
            MockMvcBuilders
                .webAppContextSetup(context)
                .addFilters<DefaultMockMvcBuilder>(securityFilterChain)
                .build()
        sellerUserId = createActiveUser()
        token = mintAccessToken(sellerUserId)
        tx.execute {
            em.persist(Seller.open(sellerUserId, "애착상회", 0L))
            val root = Category.create(null, Category.ROOT_DEPTH, "강아지", null, 1)
            em.persist(root)
            val mid = Category.create(root, Category.MID_DEPTH, "사료", null, 1)
            em.persist(mid)
            val leaf = Category.create(mid, Category.LEAF_DEPTH, "건사료", null, 1)
            em.persist(leaf)
            leafCategoryId = leaf.id

            val product = newProduct(leaf, sellerUserId)
            em.persist(product)
            productId = product.id
            publicId = product.publicId
        }
    }

    @Test
    fun `접수하면 열린 행에 이의가 붙고 상품 노출은 바뀌지 않는다`() {
        changeStatus(productId, InspectionStatus.PENDING)
        settledInspection(InspectionJudgment.FLAG)
        openReview(productId)

        mockMvc
            .perform(appealRequest(publicId, "치료 효능이 아니라 성분 설명입니다."))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.productId").value(publicId))
            .andExpect(jsonPath("$.data.progressStep").value("ADMIN_REVIEWING"))
            .andExpect(jsonPath("$.data.appealedAt").exists())
            .andExpect(jsonPath("$.data.appealReason").value("치료 효능이 아니라 성분 설명입니다."))
            // 접수는 노출을 바꾸지 않는다. 바꾸면 이의가 곧 반려 해제 수단이 된다
            .andExpect(jsonPath("$.data.inspectionStatus").value("PENDING"))

        val row = assertNotNull(findPending(productId), "열린 행이 그대로 열려 있어야 한다")
        assertNotNull(row.appealedAt)
        assertEquals("치료 효능이 아니라 성분 설명입니다.", row.appealReason)
        assertEquals(InspectionStatus.PENDING, statusOf(productId), "접수가 노출 상태를 건드리면 안 된다")
    }

    @Test
    fun `접수는 관리자 큐의 정렬 키를 밀지 않는다`() {
        changeStatus(productId, InspectionStatus.PENDING)
        val blindedAt = LocalDateTime.now().minusDays(3).withNano(0)
        openReview(productId, blindedAt)

        mockMvc.perform(appealRequest(publicId, "성분 설명입니다.")).andExpect(status().isOk)

        // blindedAt이 관리자 목록의 정렬 키다. 접수가 밀면 이의 폭주가 곧 큐 정렬 조작이 된다
        assertEquals(blindedAt, findPending(productId)!!.blindedAt)
    }

    @Test
    fun `같은 행에 두 번째 이의는 거부된다`() {
        changeStatus(productId, InspectionStatus.PENDING)
        openReview(productId)

        mockMvc.perform(appealRequest(publicId, "첫 번째 사유입니다.")).andExpect(status().isOk)

        mockMvc
            .perform(appealRequest(publicId, "두 번째 사유입니다."))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.errorCode").value(40503))

        assertEquals("첫 번째 사유입니다.", findPending(productId)!!.appealReason, "두 번째 사유가 첫 사유를 덮으면 안 된다")
    }

    @Test
    fun `관리자가 반려한 상품은 열린 행이 없어도 행을 새로 열어 받는다`() {
        changeStatus(productId, InspectionStatus.REJECTED)
        val settledAt = settledInspection(InspectionJudgment.FLAG)

        mockMvc
            .perform(appealRequest(publicId, "성분 설명을 다시 봐 주세요."))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.progressStep").value("ADMIN_REVIEWING"))

        val row = assertNotNull(findPending(productId), "반려된 상품의 이의는 관리자가 찾을 수 있는 행을 만들어야 한다")
        assertNotNull(row.appealedAt)
        // 그 칼럼의 뜻은 "행을 열었거나 다시 연 판정의 시각"이다. 접수 시각으로 채우면 뜻이 갈린다
        assertEquals(settledAt, row.blindedAt)
        assertEquals(InspectionStatus.REJECTED, statusOf(productId), "접수가 반려를 풀면 안 된다")
    }

    @Test
    fun `첫 판정을 기다리는 상품은 이의를 받지 않는다`() {
        // 상품의 PENDING은 "첫 판정 대기"와 "사람 확인 대기"를 겸한다. 열린 행이 둘을 가른다
        changeStatus(productId, InspectionStatus.PENDING)

        mockMvc
            .perform(appealRequest(publicId, "억울합니다."))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.errorCode").value(40502))

        assertNull(findPending(productId), "자격 없는 접수가 큐 행을 만들면 안 된다")
    }

    @Test
    fun `통과한 상품은 이의를 받지 않는다`() {
        mockMvc
            .perform(appealRequest(publicId, "억울합니다."))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.errorCode").value(40502))
    }

    @Test
    fun `사유가 비면 거부된다`() {
        changeStatus(productId, InspectionStatus.PENDING)
        openReview(productId)

        mockMvc
            .perform(appealRequest(publicId, "   "))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(40501))
    }

    @Test
    fun `사유를 아예 보내지 않아도 형식 오류가 아니라 사유 필수로 답한다`() {
        changeStatus(productId, InspectionStatus.PENDING)
        openReview(productId)

        mockMvc
            .perform(
                post("/api/v1/sellers/me/products/$publicId/inspection/appeals")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"),
            ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(40501))
    }

    @Test
    fun `셀러별 열린 이의 상한을 넘기면 거부된다`() {
        val second = anotherProductWithOpenReview()
        val third = anotherProductWithOpenReview()
        changeStatus(productId, InspectionStatus.PENDING)
        openReview(productId)

        mockMvc.perform(appealRequest(second, "하나")).andExpect(status().isOk)
        mockMvc.perform(appealRequest(third, "둘")).andExpect(status().isOk)

        mockMvc
            .perform(appealRequest(publicId, "셋"))
            .andExpect(status().isTooManyRequests)
            .andExpect(jsonPath("$.errorCode").value(40504))
    }

    @Test
    fun `고친 내용이 통과해 닫힌 이의는 셀러 정원을 먹지 않는다`() {
        val second = anotherProductWithOpenReview()
        val third = anotherProductWithOpenReview()
        changeStatus(productId, InspectionStatus.PENDING)
        openReview(productId)

        mockMvc.perform(appealRequest(second, "하나")).andExpect(status().isOk)
        mockMvc.perform(appealRequest(third, "둘")).andExpect(status().isOk)

        // 셀러가 고친 내용이 AI를 통과하면 그 행은 SUPERSEDED로 닫힌다. 아무도 결정할 수 없는
        // 이의가 정원을 영구히 먹으면 차단된 다른 상품까지 함께 묶인다
        tx.execute {
            val row = findPending(idOf(second))!!
            row.supersede(LocalDateTime.now())
            reviewQueue.save(row)
        }

        mockMvc.perform(appealRequest(publicId, "셋")).andExpect(status().isOk)
    }

    @Test
    fun `기각된 뒤 쿨다운 안에는 다시 제기할 수 없다`() {
        rejectAppeal(productId, resolvedAt = LocalDateTime.now().minusHours(1))
        changeStatus(productId, InspectionStatus.REJECTED)

        mockMvc
            .perform(appealRequest(publicId, "다시 봐 주세요."))
            .andExpect(status().isTooManyRequests)
            .andExpect(jsonPath("$.errorCode").value(40505))
    }

    @Test
    fun `쿨다운이 지나면 다시 제기할 수 있다`() {
        rejectAppeal(productId, resolvedAt = LocalDateTime.now().minusHours(25))
        changeStatus(productId, InspectionStatus.REJECTED)
        settledInspection(InspectionJudgment.FLAG)

        mockMvc.perform(appealRequest(publicId, "다시 봐 주세요.")).andExpect(status().isOk)
    }

    @Test
    fun `새 판정이 와 행이 갱신되면 이의가 비워져 다시 제기할 수 있다`() {
        changeStatus(productId, InspectionStatus.PENDING)
        openReview(productId)
        mockMvc.perform(appealRequest(publicId, "성분 설명입니다.")).andExpect(status().isOk)

        // 셀러가 고친 내용도 걸리면 행이 유지된다. 이의를 그대로 두면 관리자는 "셀러가 A에
        // 억울하다 했는데 지금 걸린 것은 B"를 보고, 재접수도 막혀 셀러는 할 수 있는 것이 없다
        tx.execute {
            val row = findPending(productId)!!
            row.refresh("연어 건사료 2kg", LocalDateTime.now())
            reviewQueue.save(row)
        }
        assertNull(findPending(productId)!!.appealedAt, "갱신된 행의 이의는 옛 내용을 가리킨다")

        mockMvc
            .perform(appealRequest(publicId, "고친 문구도 성분 설명입니다."))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.appealReason").value("고친 문구도 성분 설명입니다."))
    }

    @Test
    fun `남의 상품에는 이의를 걸 수 없다`() {
        val otherSellerId = createActiveUser()
        val otherPublicId =
            tx.execute {
                em.persist(Seller.open(otherSellerId, "다른상회", 0L))
                val leaf = em.find(Category::class.java, leafCategoryId)
                val product = newProduct(leaf, otherSellerId)
                em.persist(product)
                product.publicId
            }!!
        tx.execute {
            val id = idOf(otherPublicId)
            openReview(id)
        }

        mockMvc
            .perform(appealRequest(otherPublicId, "억울합니다."))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.errorCode").value(40000))
    }

    private fun appealRequest(
        target: String,
        reason: String,
    ) = post("/api/v1/sellers/me/products/$target/inspection/appeals")
        .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
        .contentType(MediaType.APPLICATION_JSON)
        .content("""{"reason":"$reason"}""")

    /** 열린 검토 행이 있는 내 상품을 하나 더 심고 공개 식별자를 돌려준다 */
    private fun anotherProductWithOpenReview(): String =
        tx.execute {
            val leaf = em.find(Category::class.java, leafCategoryId)
            val product = newProduct(leaf, sellerUserId)
            em.persist(product)
            em.flush()
            openReview(product.id)
            product.publicId
        }!!

    /** 이의를 한 번 접수했다 기각된 상태를 만든다. 쿨다운의 분모는 닫힌 시각이다 */
    private fun rejectAppeal(
        target: Long,
        resolvedAt: LocalDateTime,
    ) {
        tx.execute {
            val row = openReview(target)
            row.appeal("처음 사유입니다.", resolvedAt.minusHours(1))
            row.reject("효능 표현을 빼고 다시 등록해 주세요.", resolvedAt)
            reviewQueue.save(row)
        }
    }

    private fun newProduct(
        category: Category,
        ownerId: Long,
    ): Product =
        Product.register(
            category = category,
            sellerId = ownerId,
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

    private fun changeStatus(
        target: Long,
        status: InspectionStatus,
    ) {
        tx.execute {
            em
                .createQuery("update Product p set p.inspectionStatus = :status where p.id = :id")
                .setParameter("status", status)
                .setParameter("id", target)
                .executeUpdate()
        }
    }

    private fun statusOf(target: Long): InspectionStatus =
        tx.execute {
            em
                .createQuery("select p.inspectionStatus from Product p where p.id = :id", InspectionStatus::class.java)
                .setParameter("id", target)
                .singleResult
        }!!

    private fun idOf(targetPublicId: String): Long =
        tx.execute {
            em
                .createQuery("select p.id from Product p where p.publicId = :pid", java.lang.Long::class.java)
                .setParameter("pid", targetPublicId)
                .singleResult
                .toLong()
        }!!

    private fun findPending(target: Long): ProductReviewQueueItem? =
        tx.execute {
            em
                .createQuery(
                    "select i from ProductReviewQueueItem i where i.targetType = :tt and i.targetId = :id and i.status = :st",
                    ProductReviewQueueItem::class.java,
                ).setParameter("tt", InspectionTargetType.PRODUCT)
                .setParameter("id", target)
                .setParameter("st", ReviewQueueStatus.PENDING_REVIEW)
                .resultList
                .firstOrNull()
        }

    private fun openReview(
        target: Long,
        blindedAt: LocalDateTime = LocalDateTime.now(),
    ): ProductReviewQueueItem =
        reviewQueue.save(
            ProductReviewQueueItem.openForProduct(target, sellerUserId, "연어 건사료 2kg", blindedAt),
        )

    /** 판정이 끝난 검수 한 건을 심고 그 판정 시각을 돌려준다 */
    private fun settledInspection(judgment: InspectionJudgment): LocalDateTime {
        val settledAt = LocalDateTime.now().minusHours(2).withNano(0)
        tx.execute {
            val inspection =
                inspections.save(
                    ContentInspection.queue(
                        targetType = InspectionTargetType.PRODUCT,
                        targetRef = productId,
                        contentHash = "a".repeat(64),
                        policyVersion = "policy-v1",
                        targetVersionNo = 1,
                    ),
                )
            inspection.settle(
                judgment = judgment,
                promptVersion = "inspection-v1+policy-v1",
                reasonCode = "MEDICAL_CLAIM",
                reasonSummary = "관절염 치료에 효과",
                sellerNotice = "치료 효능 표현을 고쳐 주세요.",
                now = settledAt,
            )
            inspections.save(inspection)
        }
        return settledAt
    }
}
