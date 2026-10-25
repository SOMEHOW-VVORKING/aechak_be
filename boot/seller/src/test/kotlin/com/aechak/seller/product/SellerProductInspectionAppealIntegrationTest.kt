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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
 * 노출 게이트 스위치를 켜지 않고 돈다(테스트 기본값은 꺼짐). 다만 여기서 심는 상태는 손으로
 * 만든 것이고, **그 상태가 스위치 꺼진 환경에서 실제로 생기는지**는 이 수트가 보지 못한다 —
 * 그 증명은 판정 수신 경로로 행을 만드는 api 쪽 수트에 있다. 여기서 보는 것은 심어 둔 상태에서
 * 접수가 어떻게 동작하는가다.
 *
 * 열린 행 수는 세어서 단언한다. 조회 헬퍼가 조용히 하나만 골라 주면 행이 둘로 갈라진 것이
 * 보이지 않는데, 그것이 이 기능에서 실제로 났던 결함 모양이다.
 *
 * 상한을 2로 낮춰 돌린다. 기본값 3으로는 상한 테스트가 상품을 넷 심어야 해서 의도가 흐려진다.
 */
@TestPropertySource(
    properties = [
        "inspection.appeal.max-open-per-seller=2",
        "inspection.appeal.cooldown-hours=24",
        "inspection.appeal.max-attempts-per-item=2",
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

    /** 경계를 1시간·25시간이 아니라 정확히 설정값으로 본다. 그러지 않으면 상한이 틀려도 통과한다 */
    @Test
    fun `쿨다운 경계는 설정한 24시간이다`() {
        rejectAppeal(productId, resolvedAt = LocalDateTime.now().minusHours(24).plusMinutes(1))
        changeStatus(productId, InspectionStatus.REJECTED)

        mockMvc
            .perform(appealRequest(publicId, "1분 남았습니다."))
            .andExpect(status().isTooManyRequests)
            .andExpect(jsonPath("$.errorCode").value(40505))
    }

    @Test
    fun `쿨다운이 지나면 다시 제기할 수 있다`() {
        rejectAppeal(productId, resolvedAt = LocalDateTime.now().minusHours(24))
        changeStatus(productId, InspectionStatus.REJECTED)
        settledInspection(InspectionJudgment.FLAG)

        mockMvc.perform(appealRequest(publicId, "다시 봐 주세요.")).andExpect(status().isOk)
    }

    /**
     * 셀러가 이의 버튼을 두 번 누른 경우.
     *
     * 상품 행 잠금만으로는 모자랐다. 파사드 트랜잭션이 잠금을 쥐기 전에 일반 조회를 넷 돌아
     * REPEATABLE READ 스냅샷이 먼저 고정되고, 그 뒤 열린 행 조회가 비잠금 일관 읽기라 먼저
     * 커밋한 쪽이 넣은 행을 보지 못했다. 그러면 한 상품에 PENDING 행이 둘 생기고, 관리자가
     * 닫는 것은 그중 하나뿐이라 남은 행이 셀러 정원을 영구히 먹는다.
     *
     * **이 테스트가 고정하는 것은 접수 경로의 격리 수준이다.** 장벽이 없어 두 트랜잭션이 실제로
     * 겹쳤는지는 보장하지 않고, 겹치지 않으면 두 번째가 순차 재접수로 거부돼 그대로 통과한다.
     * 겹쳤을 때 갈라지지 않는 근거도 잠금 조회 자체가 아니라 READ COMMITTED에서 매 구문이
     * 최신 커밋을 읽는 것이다. 잠금 조회가 쓸모를 갖는 자리는 격리 수준을 올리지 않은 경로
     * (관리자 결정)이고, 간극 잠금을 쥐지 않는지는 판정 경로의 api 쪽 수트가 본다.
     */
    @Test
    fun `같은 상품에 동시 접수가 들어와도 열린 행과 이의는 하나다`() {
        changeStatus(productId, InspectionStatus.REJECTED)
        settledInspection(InspectionJudgment.FLAG)

        val statuses = appealTwiceInParallel("첫 번째 사유입니다.", "두 번째 사유입니다.")

        assertEquals(1, pendingCount(productId), "열린 행이 둘이면 관리자가 닫지 못한 행이 정원을 영구히 먹는다")
        assertEquals(1, statuses.count { it == 200 }, "접수는 한 건만 성공해야 한다. 받은 상태=$statuses")
        assertEquals(1, appealedCount(sellerUserId), "정원의 분모가 둘이면 상한이 한 번 누르는 것으로 깎인다")
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
            row.refresh("연어 건사료 2kg", LocalDateTime.now(), "b".repeat(64))
            reviewQueue.save(row)
        }
        assertNull(findPending(productId)!!.appealedAt, "갱신된 행의 이의는 옛 내용을 가리킨다")

        mockMvc
            .perform(appealRequest(publicId, "고친 문구도 성분 설명입니다."))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.appealReason").value("고친 문구도 성분 설명입니다."))
    }

    /**
     * "고치고 또 접수"를 반복하는 되돌이.
     *
     * 무효화는 셀러에게 재접수를 열어 주는 장치고 그래야 하는 이유가 있다 — 결정할 수 없게 된
     * 이의가 정원을 영구히 먹으면 차단의 유일한 탈출 경로가 막힌다. 그런데 무효화가 라이브
     * 칼럼을 비우는 일이라, 비우는 것이 곧 상한의 분모를 지우는 일이기도 하다. 그러면 한 글자만
     * 고쳐 새 판정을 받는 것으로 정원·중복·쿨다운 어디에도 걸리지 않고 무한히 다시 접수할 수
     * 있고, 한 바퀴마다 관리자가 새 사유를 읽고 검수 비용 한 번이 플랫폼 부담으로 든다.
     *
     * 그래서 접수 **시도 횟수**를 행이 따로 들고, 무효화가 그 숫자를 지우지 않는다.
     */
    @Test
    fun `무효화를 반복해도 상품당 접수 횟수 상한에서 끊긴다`() {
        changeStatus(productId, InspectionStatus.PENDING)
        openReview(productId)

        repeat(MAX_ATTEMPTS_PER_ITEM) { round ->
            mockMvc.perform(appealRequest(publicId, "$round 번째 사유입니다.")).andExpect(status().isOk)
            invalidateByNewContent(round)
        }

        mockMvc
            .perform(appealRequest(publicId, "또 접수합니다."))
            .andExpect(status().isTooManyRequests)
            .andExpect(jsonPath("$.errorCode").value(40507))
    }

    /** 셀러가 내용을 고쳐 새 판정이 온 것과 같은 상태 — 열린 행은 그대로고 이의만 무효가 된다 */
    private fun invalidateByNewContent(round: Int) {
        tx.execute {
            val row = findPending(productId)!!
            row.refresh("연어 건사료 2kg", LocalDateTime.now(), "c$round".padEnd(64, 'c'))
            reviewQueue.save(row)
        }
        assertNull(findPending(productId)!!.appealedAt, "무효화가 되지 않으면 이 되돌이 자체가 성립하지 않는다")
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
            row.appeal("처음 사유입니다.", resolvedAt.minusHours(1), null)
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

    /** 열린 행을 돌려준다. 둘 이상이면 바로 깨뜨린다 — 조용히 하나만 고르면 중복이 보이지 않는다 */
    private fun findPending(target: Long): ProductReviewQueueItem? =
        tx.execute {
            val rows =
                em
                    .createQuery(
                        "select i from ProductReviewQueueItem i where i.targetType = :tt and i.targetId = :id and i.status = :st",
                        ProductReviewQueueItem::class.java,
                    ).setParameter("tt", InspectionTargetType.PRODUCT)
                    .setParameter("id", target)
                    .setParameter("st", ReviewQueueStatus.PENDING_REVIEW)
                    .resultList
            assertTrue(rows.size <= 1, "상품마다 열린 행은 하나여야 한다. 실제=${rows.size}")
            rows.firstOrNull()
        }

    private fun pendingCount(target: Long): Long =
        tx.execute {
            em
                .createQuery(
                    "select count(i) from ProductReviewQueueItem i " +
                        "where i.targetType = :tt and i.targetId = :id and i.status = :st",
                    java.lang.Long::class.java,
                ).setParameter("tt", InspectionTargetType.PRODUCT)
                .setParameter("id", target)
                .setParameter("st", ReviewQueueStatus.PENDING_REVIEW)
                .singleResult
                .toLong()
        }!!

    /** 셀러가 지금 열어 둔 이의 건수. 폭주 상한의 분모와 같은 술어다 */
    private fun appealedCount(owner: Long): Long =
        tx.execute {
            em
                .createQuery(
                    "select count(i) from ProductReviewQueueItem i where i.targetType = :tt and i.ownerId = :owner " +
                        "and i.status = :st and i.appealedAt is not null",
                    java.lang.Long::class.java,
                ).setParameter("tt", InspectionTargetType.PRODUCT)
                .setParameter("owner", owner)
                .setParameter("st", ReviewQueueStatus.PENDING_REVIEW)
                .singleResult
                .toLong()
        }!!

    /** 같은 상품에 두 요청을 겹쳐 보내고 응답 상태를 돌려준다 */
    private fun appealTwiceInParallel(
        first: String,
        second: String,
    ): List<Int> {
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val tasks =
                listOf(first, second).map { reason ->
                    pool.submit<Int> {
                        start.await()
                        mockMvc
                            .perform(appealRequest(publicId, reason))
                            .andReturn()
                            .response
                            .status
                    }
                }
            start.countDown()
            return tasks.map { it.get(30, TimeUnit.SECONDS) }
        } finally {
            pool.shutdownNow()
        }
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

    private companion object {
        /** 위 [TestPropertySource]의 `max-attempts-per-item`과 같은 값이어야 한다 */
        const val MAX_ATTEMPTS_PER_ITEM = 2
    }
}
