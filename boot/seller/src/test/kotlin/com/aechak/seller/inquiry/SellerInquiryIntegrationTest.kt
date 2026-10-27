package com.aechak.seller.inquiry

import com.aechak.api.support.IntegrationTestBase
import com.aechak.application.inquiry.product.usecase.ProductInquiryUseCase
import com.aechak.application.inquiry.product.usecase.SellerProductInquiryUseCase
import com.aechak.application.inquiry.product.usecase.command.AnswerProductInquiryCommand
import com.aechak.application.inquiry.product.usecase.command.WithdrawProductInquiryCommand
import com.aechak.common.error.BusinessException
import com.aechak.domain.inquiry.error.InquiryErrorCode
import com.aechak.domain.inquiry.product.ProductInquiry
import com.aechak.domain.inquiry.product.enums.ProductInquiryCategory
import com.aechak.domain.inquiry.product.enums.ProductInquiryStatus
import com.aechak.domain.product.category.Category
import com.aechak.domain.product.product.Product
import com.aechak.domain.seller.seller.Seller
import com.aechak.domain.seller.seller.enums.SellerStatus
import com.aechak.domain.user.user.User
import com.jayway.jsonpath.JsonPath
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.web.FilterChainProxy
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.time.LocalDateTime
import java.util.concurrent.Callable
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * 셀러가 받은 상품 문의 통합 테스트.
 * 소유권(남의 상품 문의는 보이지도 답할 수도 없다), 지표 세 개, 상태·유형·검색 필터, 상태 전이 금지 조합을 고정한다.
 */
class SellerInquiryIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var context: WebApplicationContext

    @Autowired
    private lateinit var securityFilterChain: FilterChainProxy

    // 동시성 테스트는 HTTP를 거치지 않고 유스케이스를 두 스레드에서 직접 부른다 —
    // MockMvc는 요청을 호출 스레드에서 처리해 두 요청을 겹치게 둘 수 없다.
    @Autowired
    private lateinit var sellerInquiryUseCase: SellerProductInquiryUseCase

    @Autowired
    private lateinit var buyerInquiryUseCase: ProductInquiryUseCase

    private lateinit var mockMvc: MockMvc
    private var sellerUserId = 0L
    private lateinit var sellerToken: String
    private var otherSellerUserId = 0L
    private lateinit var otherSellerToken: String
    private var buyerUserId = 0L
    private var categoryId = 0L
    private lateinit var myProduct: ProductRef
    private lateinit var otherProduct: ProductRef

    private data class ProductRef(
        val id: Long,
        val publicId: String,
        val name: String,
        val sellerId: Long,
    )

    @BeforeEach
    fun setUp() {
        mockMvc =
            MockMvcBuilders
                .webAppContextSetup(context)
                .addFilters<DefaultMockMvcBuilder>(securityFilterChain)
                .build()
        sellerUserId = tx.execute { persistUser("스토어장") }!!
        otherSellerUserId = tx.execute { persistUser("남의스토어") }!!
        buyerUserId = tx.execute { persistUser("김하늘") }!!
        sellerToken = mintAccessToken(sellerUserId)
        otherSellerToken = mintAccessToken(otherSellerUserId)
        tx.execute {
            val root = Category.create(null, Category.ROOT_DEPTH, "강아지", null, 1)
            em.persist(root)
            val mid = Category.create(root, Category.MID_DEPTH, "식기", null, 1)
            em.persist(mid)
            em.flush()
            categoryId = mid.id
            openSeller(sellerUserId, "애착스토어")
            openSeller(otherSellerUserId, "남의스토어")
            myProduct = persistProduct(sellerUserId, "자동 급식기 5L")
            otherProduct = persistProduct(otherSellerUserId, "원목 캣타워")
        }
    }

    @Test
    fun `내 상품에 온 문의만 보인다`() {
        seed(myProduct, title = "내 상품 문의")
        seed(otherProduct, title = "남의 상품 문의")

        val body = search()

        assertEquals(listOf("내 상품 문의"), JsonPath.read<List<String>>(body, "$.data.inquiries[*].title"))
        assertEquals(1, JsonPath.read<Int>(body, "$.data.totalCount"))
        assertEquals(myProduct.publicId, JsonPath.read<String>(body, "$.data.inquiries[0].productId"))
        assertEquals("자동 급식기 5L", JsonPath.read<String>(body, "$.data.inquiries[0].productName"))
    }

    @Test
    fun `지표는 답변 대기와 기한 지남과 전체를 서버가 센다`() {
        seed(myProduct, title = "기한 안 지난 대기")
        seed(myProduct, title = "기한 지난 대기", askedAt = LocalDateTime.now().minusDays(10))
        val answered = seed(myProduct, title = "답변 완료")
        answer(answered).andExpect(status().isCreated)
        seed(otherProduct, title = "남의 문의", askedAt = LocalDateTime.now().minusDays(10))

        val body = search()

        assertEquals(2, JsonPath.read<Int>(body, "$.data.metrics.pending"))
        assertEquals(1, JsonPath.read<Int>(body, "$.data.metrics.overdue"))
        assertEquals(3, JsonPath.read<Int>(body, "$.data.metrics.total"))
    }

    @Test
    fun `문의가 하나도 없는 셀러도 지표가 0으로 내려온다`() {
        // 집계는 조건부 합계라 대상 행이 없으면 SUM이 NULL이다 — 쿼리에서 0으로 못박지 않으면 터진다.
        val body = search()

        assertEquals(0, JsonPath.read<Int>(body, "$.data.metrics.pending"))
        assertEquals(0, JsonPath.read<Int>(body, "$.data.metrics.overdue"))
        assertEquals(0, JsonPath.read<Int>(body, "$.data.metrics.total"))
        assertEquals(0, JsonPath.read<Int>(body, "$.data.totalCount"))
    }

    @Test
    fun `행의 기한 지남은 지표와 같은 기준으로 내려간다`() {
        seed(myProduct, title = "기한 지난 대기", askedAt = LocalDateTime.now().minusDays(10))
        seed(myProduct, title = "기한 안 지난 대기")

        val body = search()

        val overdueByTitle =
            JsonPath
                .read<List<String>>(body, "$.data.inquiries[*].title")
                .zip(JsonPath.read<List<Boolean>>(body, "$.data.inquiries[*].overdue"))
                .toMap()
        assertEquals(mapOf("기한 지난 대기" to true, "기한 안 지난 대기" to false), overdueByTitle)
        assertEquals(
            JsonPath.read<List<Boolean>>(body, "$.data.inquiries[*].overdue").count { it },
            JsonPath.read<Int>(body, "$.data.metrics.overdue"),
        )
    }

    @Test
    fun `답변하면 답변 완료가 되고 답변 대기 지표가 줄어든다`() {
        val publicId = seed(myProduct, title = "크기 문의")

        answer(publicId, content = "입구 높이는 12cm 입니다")
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.data.status").value(ProductInquiryStatus.ANSWERED.name))
            .andExpect(jsonPath("$.data.answeredAt").exists())

        val stored =
            tx.execute {
                val inquiry = loadInquiry(publicId)!!
                Triple(inquiry.status, inquiry.answer?.content, inquiry.answer?.answererUserId)
            }!!
        assertEquals(ProductInquiryStatus.ANSWERED, stored.first)
        assertEquals("입구 높이는 12cm 입니다", stored.second)
        assertEquals(sellerUserId, stored.third)

        val body = search()
        assertEquals(0, JsonPath.read<Int>(body, "$.data.metrics.pending"))
        assertEquals(1, JsonPath.read<Int>(body, "$.data.metrics.total"))
        assertEquals("입구 높이는 12cm 입니다", JsonPath.read<String>(body, "$.data.inquiries[0].answer.content"))
        assertFalse(JsonPath.read<Boolean>(body, "$.data.inquiries[0].overdue"))
    }

    @Test
    fun `남의 상품에 온 문의에는 답변할 수 없고 답변이 달리지 않는다`() {
        val publicId = seed(otherProduct, title = "남의 상품 문의")

        answer(publicId)
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.errorCode").value(120108))

        val stored = tx.execute { loadInquiry(publicId)!!.let { it.status to it.answer?.content } }!!
        assertEquals(ProductInquiryStatus.RECEIVED, stored.first)
        assertNull(stored.second)
    }

    @Test
    fun `이미 답변한 문의에 다시 답변하면 거절되고 첫 답변이 남는다`() {
        val publicId = seed(myProduct)
        answer(publicId, content = "첫 답변").andExpect(status().isCreated)

        answer(publicId, content = "두 번째 답변")
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.errorCode").value(120109))

        assertEquals("첫 답변", tx.execute { loadInquiry(publicId)!!.answer?.content })
    }

    @Test
    fun `철회된 문의에는 답변할 수 없고 목록과 지표에서도 빠진다`() {
        val publicId = seed(myProduct, title = "철회될 문의")
        tx.execute { loadInquiry(publicId)!!.withdraw(LocalDateTime.now()) }

        answer(publicId)
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.errorCode").value(120114))

        val body = search()
        assertEquals(0, JsonPath.read<List<*>>(body, "$.data.inquiries").size)
        assertEquals(0, JsonPath.read<Int>(body, "$.data.metrics.total"))
        assertEquals(0, JsonPath.read<Int>(body, "$.data.metrics.pending"))
    }

    @Test
    fun `상태 탭은 답변 대기와 답변 완료를 가르고 지표는 흔들리지 않는다`() {
        seed(myProduct, title = "대기 문의")
        val answered = seed(myProduct, title = "완료 문의")
        answer(answered).andExpect(status().isCreated)

        val pending = search("status" to "RECEIVED")
        assertEquals(listOf("대기 문의"), JsonPath.read<List<String>>(pending, "$.data.inquiries[*].title"))
        assertEquals(2, JsonPath.read<Int>(pending, "$.data.metrics.total"))

        val done = search("status" to "ANSWERED")
        assertEquals(listOf("완료 문의"), JsonPath.read<List<String>>(done, "$.data.inquiries[*].title"))
        assertEquals(2, JsonPath.read<Int>(done, "$.data.metrics.total"))
    }

    @Test
    fun `유형 필터와 검색어가 실제로 걸린다`() {
        seed(myProduct, title = "상품 크기 문의", category = ProductInquiryCategory.PRODUCT)
        seed(myProduct, title = "배송비 문의", category = ProductInquiryCategory.DELIVERY)

        assertEquals(
            listOf("배송비 문의"),
            JsonPath.read<List<String>>(search("category" to "DELIVERY"), "$.data.inquiries[*].title"),
        )
        assertEquals(
            listOf("상품 크기 문의"),
            JsonPath.read<List<String>>(search("keyword" to "크기"), "$.data.inquiries[*].title"),
        )
        // 검색어는 상품명도 본다 — 화면 문구가 "문의 제목이나 상품명으로 찾기"다
        assertEquals(2, JsonPath.read<List<*>>(search("keyword" to "급식기"), "$.data.inquiries").size)
        assertEquals(0, JsonPath.read<List<*>>(search("keyword" to "없는말"), "$.data.inquiries").size)
    }

    @Test
    fun `비밀글도 셀러에게는 본문이 내려가고 작성자는 마스킹된다`() {
        seed(myProduct, title = "비밀 문의", content = "비밀 내용", secret = true)

        val body = search()

        assertTrue(JsonPath.read<Boolean>(body, "$.data.inquiries[0].secret"))
        assertEquals("비밀 문의", JsonPath.read<String>(body, "$.data.inquiries[0].title"))
        assertEquals("비밀 내용", JsonPath.read<String>(body, "$.data.inquiries[0].content"))
        assertEquals("김**", JsonPath.read<String>(body, "$.data.inquiries[0].authorMasked"))
    }

    // 답변 본문 규칙은 ProductInquiry가 소유하고 전용 코드를 던진다. 요청 DTO에 같은 제약을
    // 거울로 두면 어노테이션이 먼저 걸려 90001이 나가고 OpenAPI·FE가 약속한 코드가 거짓이 된다
    // (30-boot §3-1). 그 거울이 다시 생기면 이 두 단언이 깨진다.
    @Test
    fun `답변 본문의 공백·길이 위반은 도메인 에러코드로 거절되고 답변이 달리지 않는다`() {
        val publicId = seed(myProduct)

        answer(publicId, content = "   ")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(120105))

        answer(publicId, content = "가".repeat(ProductInquiry.ANSWER_CONTENT_MAX + 1))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(120106))

        val stored = tx.execute { loadInquiry(publicId)!!.let { it.status to it.answers.size } }!!
        assertEquals(ProductInquiryStatus.RECEIVED, stored.first)
        assertEquals(0, stored.second)
    }

    /**
     * 답변과 철회가 같은 문의에 동시에 들어오는 경우. 잠그지 않고 읽으면 둘 다 RECEIVED를 보고
     * 둘 다 성공해 **답변이 달린 WITHDRAWN**이 남는다 — 셀러가 쓴 글이 목록에서 사라지는데
     * 그것을 막으려고 둔 상태 가드가 통째로 무력해진 상태다. 어느 쪽이 먼저 잠그는지는 보장하지
     * 않으므로 "한쪽만 성공하고 최종 상태가 답변 유무와 일치한다"를 단언한다.
     */
    @Test
    fun `답변과 철회가 동시에 들어오면 한쪽만 성공하고 답변이 달린 철회가 남지 않는다`() {
        val publicId = seed(myProduct, title = "동시 처리 문의")

        val outcomes =
            runConcurrently(
                { sellerInquiryUseCase.answerProductInquiry(answerCommand(publicId)) },
                { buyerInquiryUseCase.withdrawProductInquiry(withdrawCommand(publicId)) },
            )

        assertEquals(1, outcomes.count { it == null }, "한쪽만 성공해야 한다: $outcomes")
        val loser = outcomes.filterNotNull().single()
        assertTrue(loser is BusinessException, "패자는 도메인 거절이어야 한다: $loser")
        val code = (loser as BusinessException).errorCode
        assertTrue(
            code == InquiryErrorCode.PRODUCT_INQUIRY_WITHDRAWN ||
                code == InquiryErrorCode.PRODUCT_INQUIRY_CANNOT_WITHDRAW_ANSWERED,
            "패자 코드가 상태 가드의 것이어야 한다: $code",
        )

        val stored = tx.execute { loadInquiry(publicId)!!.let { Triple(it.status, it.answers.size, it.answeredAt) } }!!
        if (stored.first == ProductInquiryStatus.WITHDRAWN) {
            assertEquals(0, stored.second, "철회로 끝났으면 답변이 남아 있으면 안 된다")
            assertNull(stored.third)
        } else {
            assertEquals(ProductInquiryStatus.ANSWERED, stored.first)
            assertEquals(1, stored.second)
        }
    }

    /**
     * 같은 문의에 답변 두 건이 동시에 들어오는 경우(답변 버튼 두 번 누르기). 잠그지 않으면
     * 둘 다 상태 가드를 통과해 자식 INSERT가 UNIQUE(inquiry_id)에서 터지고, 그 예외를 번역하는
     * 처리가 없어 패자가 409가 아니라 500을 받는다. 패자의 예외 **타입**까지 단언해야 그 경로를 잡는다.
     */
    @Test
    fun `같은 문의에 답변이 동시에 두 번 들어오면 한 건만 달리고 패자는 이미 답변함으로 거절된다`() {
        val publicId = seed(myProduct, title = "두 번 눌린 문의")

        val outcomes =
            runConcurrently(
                { sellerInquiryUseCase.answerProductInquiry(answerCommand(publicId, "첫 답변")) },
                { sellerInquiryUseCase.answerProductInquiry(answerCommand(publicId, "두 번째 답변")) },
            )

        assertEquals(1, outcomes.count { it == null }, "한 건만 성공해야 한다: $outcomes")
        val loser = outcomes.filterNotNull().single()
        assertTrue(loser is BusinessException, "패자는 409(이미 답변함)여야 한다 — DB 무결성 예외가 새면 500이 된다: $loser")
        assertEquals(InquiryErrorCode.PRODUCT_INQUIRY_ALREADY_ANSWERED, (loser as BusinessException).errorCode)

        assertEquals(1, tx.execute { loadInquiry(publicId)!!.answers.size })
    }

    @Test
    fun `오프셋 페이지네이션과 페이지 정보가 동작한다`() {
        repeat(3) { seed(myProduct, title = "문의$it") }

        val page0 = search("size" to "2")
        assertEquals(2, JsonPath.read<List<*>>(page0, "$.data.inquiries").size)
        assertEquals(3, JsonPath.read<Int>(page0, "$.data.totalCount"))
        assertEquals(2, JsonPath.read<Int>(page0, "$.data.totalPages"))
        assertTrue(JsonPath.read<Boolean>(page0, "$.data.hasNext"))

        val page1 = search("size" to "2", "page" to "1")
        assertEquals(1, JsonPath.read<List<*>>(page1, "$.data.inquiries").size)
        assertFalse(JsonPath.read<Boolean>(page1, "$.data.hasNext"))
    }

    @Test
    fun `정지된 셀러는 받은 문의를 읽지 못하고 답변도 못 한다`() {
        val publicId = seed(myProduct)
        tx.execute { setSellerStatus(sellerUserId, SellerStatus.SUSPENDED) }

        mockMvc
            .perform(get(LIST_PATH).header(HttpHeaders.AUTHORIZATION, "Bearer $sellerToken"))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.errorCode").value(120112))
        answer(publicId)
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.errorCode").value(120112))

        assertNull(tx.execute { loadInquiry(publicId)!!.answer?.content })
    }

    @Test
    fun `휴점 셀러는 받은 문의를 읽을 수 있다`() {
        seed(myProduct, title = "휴점 중 받은 문의")
        tx.execute { setSellerStatus(sellerUserId, SellerStatus.PAUSED) }

        assertEquals(
            listOf("휴점 중 받은 문의"),
            JsonPath.read<List<String>>(search(), "$.data.inquiries[*].title"),
        )
    }

    /** 두 작업을 배리어로 맞춰 띄우고 각자의 예외(없으면 null)를 호출 순서대로 돌려준다. */
    private fun runConcurrently(vararg actions: () -> Unit): List<Throwable?> {
        val barrier = CyclicBarrier(actions.size)
        val pool = Executors.newFixedThreadPool(actions.size)
        try {
            val futures =
                actions.map { action ->
                    pool.submit(
                        Callable<Throwable?> {
                            barrier.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                            runCatching { action() }.exceptionOrNull()
                        },
                    )
                }
            return futures.map { it.get(TIMEOUT_SECONDS, TimeUnit.SECONDS) }
        } finally {
            pool.shutdownNow()
        }
    }

    private fun answerCommand(
        inquiryPublicId: String,
        content: String = "답변 드립니다",
    ) = AnswerProductInquiryCommand(sellerId = sellerUserId, inquiryPublicId = inquiryPublicId, content = content)

    private fun withdrawCommand(inquiryPublicId: String) =
        WithdrawProductInquiryCommand(authorUserId = buyerUserId, inquiryPublicId = inquiryPublicId)

    private fun search(vararg params: Pair<String, String>): String {
        val request = get(LIST_PATH).header(HttpHeaders.AUTHORIZATION, "Bearer $sellerToken")
        params.forEach { (key, value) -> request.param(key, value) }
        return mockMvc
            .perform(request)
            .andExpect(status().isOk)
            .andReturn()
            .response
            .getContentAsString(Charsets.UTF_8)
    }

    private fun answer(
        inquiryPublicId: String,
        content: String = "답변 드립니다",
        asToken: String? = null,
    ): ResultActions =
        mockMvc.perform(
            post("$LIST_PATH/$inquiryPublicId/answer")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${asToken ?: sellerToken}")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"content":"$content"}"""),
        )

    private fun seed(
        product: ProductRef,
        title: String = "크기 문의",
        content: String = "입구 높이가 몇 cm 인가요",
        secret: Boolean = false,
        category: ProductInquiryCategory = ProductInquiryCategory.PRODUCT,
        askedAt: LocalDateTime = LocalDateTime.now(),
    ): String =
        tx.execute {
            val inquiry =
                ProductInquiry.ask(
                    productId = product.id,
                    productPublicId = product.publicId,
                    sellerId = product.sellerId,
                    authorUserId = buyerUserId,
                    productNameSnapshot = product.name,
                    category = category,
                    title = title,
                    content = content,
                    secret = secret,
                    now = askedAt,
                )
            em.persist(inquiry)
            em.flush()
            inquiry.publicId
        }!!

    private fun loadInquiry(publicId: String): ProductInquiry? =
        em
            .createQuery("select i from ProductInquiry i where i.publicId = :pid", ProductInquiry::class.java)
            .setParameter("pid", publicId)
            .resultList
            .firstOrNull()

    private fun persistUser(nickname: String): Long {
        val user = User.preRegister()
        em.persist(user)
        em.flush()
        user.completeOnboarding(nickname)
        em.flush()
        return user.id
    }

    private fun openSeller(
        userId: Long,
        storeName: String,
    ) {
        em.persist(Seller.open(userId = userId, storeName = storeName, baseShippingFee = 3000L))
        em.flush()
    }

    private fun setSellerStatus(
        userId: Long,
        status: SellerStatus,
    ) {
        em
            .createQuery("update Seller s set s.status = :st, s.updatedAt = :now where s.userId = :id")
            .setParameter("st", status)
            .setParameter("now", LocalDateTime.now())
            .setParameter("id", userId)
            .executeUpdate()
    }

    private fun persistProduct(
        sellerId: Long,
        name: String,
    ): ProductRef {
        val category = em.find(Category::class.java, categoryId)
        val product =
            Product.register(
                category = category,
                sellerId = sellerId,
                name = name,
                description = null,
                representativeImageKey = "products/$name.jpg",
                regularPrice = 10000L,
                discountPrice = null,
                discountStartAt = null,
                discountEndAt = null,
            )
        em.persist(product)
        em.flush()
        return ProductRef(id = product.id, publicId = product.publicId, name = name, sellerId = sellerId)
    }

    companion object {
        private const val LIST_PATH = "/api/v1/sellers/me/inquiries"

        /** 잠금 대기가 실제로 풀리는지 보는 테스트라 넉넉히 두되 무한정 매달리지는 않게 한다. */
        private const val TIMEOUT_SECONDS = 20L
    }
}
