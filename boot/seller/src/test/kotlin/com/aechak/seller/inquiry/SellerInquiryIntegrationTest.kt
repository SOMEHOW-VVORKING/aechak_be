package com.aechak.seller.inquiry

import com.aechak.api.support.IntegrationTestBase
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

/**
 * 셀러가 받은 상품 문의 통합 테스트.
 * 소유권(남의 상품 문의는 보이지도 답할 수도 없다), 지표 세 개, 상태·유형·검색 필터, 상태 전이 금지 조합을 고정한다.
 */
class SellerInquiryIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var context: WebApplicationContext

    @Autowired
    private lateinit var securityFilterChain: FilterChainProxy

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
    }
}
