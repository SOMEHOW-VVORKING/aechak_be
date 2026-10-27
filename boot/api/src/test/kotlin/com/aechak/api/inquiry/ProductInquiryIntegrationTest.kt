package com.aechak.api.inquiry

import com.aechak.api.support.IntegrationTestBase
import com.aechak.domain.inquiry.product.ProductInquiry
import com.aechak.domain.inquiry.product.enums.ProductInquiryCategory
import com.aechak.domain.inquiry.product.enums.ProductInquiryStatus
import com.aechak.domain.product.category.Category
import com.aechak.domain.product.product.Product
import com.aechak.domain.product.product.enums.SaleStatus
import com.aechak.domain.seller.seller.Seller
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.time.LocalDateTime

/**
 * 구매자 쪽 상품 문의 통합 테스트.
 * 비밀글 가리기(남의 것은 안 보이고 작성자·셀러는 보인다), 철회 권한, 상태 전이 금지 조합을 고정한다.
 */
class ProductInquiryIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var context: WebApplicationContext

    @Autowired
    private lateinit var securityFilterChain: FilterChainProxy

    private lateinit var mockMvc: MockMvc
    private var sellerUserId = 0L
    private lateinit var sellerToken: String
    private var buyerUserId = 0L
    private lateinit var buyerToken: String
    private var otherBuyerUserId = 0L
    private lateinit var otherBuyerToken: String
    private lateinit var productPublicId: String
    private var productId = 0L

    @BeforeEach
    fun setUp() {
        mockMvc =
            MockMvcBuilders
                .webAppContextSetup(context)
                .addFilters<DefaultMockMvcBuilder>(securityFilterChain)
                .build()
        sellerUserId = tx.execute { persistUser("스토어장") }!!
        buyerUserId = tx.execute { persistUser("김하늘") }!!
        otherBuyerUserId = tx.execute { persistUser("이구름") }!!
        sellerToken = mintAccessToken(sellerUserId)
        buyerToken = mintAccessToken(buyerUserId)
        otherBuyerToken = mintAccessToken(otherBuyerUserId)
        tx.execute {
            val product = persistVisibleProduct("자동 급식기 5L")
            em.flush()
            productPublicId = product.publicId
            productId = product.id
        }
    }

    @Test
    fun `문의를 등록하면 답변 대기 상태와 서버가 계산한 답변 기한을 돌려준다`() {
        val body =
            ask(title = "소형견도 쓸 수 있나요")
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.data.status").value(ProductInquiryStatus.RECEIVED.name))
                .andExpect(jsonPath("$.data.answerDueAt").exists())
                .andReturn()
                .response
                .getContentAsString(Charsets.UTF_8)

        val publicId = JsonPath.read<String>(body, "$.data.inquiryId")
        val stored = tx.execute { loadInquiry(publicId) }!!
        assertEquals(productId, stored.productId)
        assertEquals(productPublicId, stored.productPublicId)
        assertEquals(sellerUserId, stored.sellerId)
        assertEquals(buyerUserId, stored.authorUserId)
        assertEquals("자동 급식기 5L", stored.productNameSnapshot)
        assertEquals(ProductInquiry.answerDeadline(stored.createdAt), stored.answerDueAt)
    }

    @Test
    fun `노출되지 않는 상품에는 문의를 등록할 수 없다`() {
        tx.execute {
            em
                .createQuery("update Product p set p.saleStatus = :st where p.id = :id")
                .setParameter("st", SaleStatus.SUSPENDED)
                .setParameter("id", productId)
                .executeUpdate()
        }

        ask().andExpect(status().isNotFound)

        assertEquals(0L, tx.execute { countInquiries() })
    }

    @Test
    fun `자기 상품에는 문의를 등록할 수 없다`() {
        ask(asToken = sellerToken)
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(120113))

        assertEquals(0L, tx.execute { countInquiries() })
    }

    @Test
    fun `상품 목록에서 남의 비밀글은 제목과 본문이 가려지고 답변도 내려가지 않는다`() {
        val publicId = askSecretByOtherBuyerAndAnswer()

        val body = listProductInquiries(asToken = buyerToken)

        assertEquals(1, JsonPath.read<List<*>>(body, "$.data.inquiries").size)
        assertEquals(publicId, JsonPath.read<String>(body, "$.data.inquiries[0].inquiryId"))
        assertEquals(ProductInquiry.SECRET_PLACEHOLDER, JsonPath.read<String>(body, "$.data.inquiries[0].title"))
        assertEquals(ProductInquiry.SECRET_PLACEHOLDER, JsonPath.read<String>(body, "$.data.inquiries[0].content"))
        assertNull(JsonPath.read<Any?>(body, "$.data.inquiries[0].answer"))
        assertFalse(JsonPath.read<Boolean>(body, "$.data.inquiries[0].readable"))
        assertFalse(JsonPath.read<Boolean>(body, "$.data.inquiries[0].mine"))
        assertTrue(JsonPath.read<Boolean>(body, "$.data.inquiries[0].secret"))
    }

    @Test
    fun `비밀글은 작성자 본인과 해당 셀러에게는 본문과 답변이 그대로 보인다`() {
        askSecretByOtherBuyerAndAnswer()

        listOf(otherBuyerToken, sellerToken).forEach { token ->
            val body = listProductInquiries(asToken = token)

            assertEquals("비밀 문의", JsonPath.read<String>(body, "$.data.inquiries[0].title"))
            assertEquals("남에게 보이면 안 되는 내용", JsonPath.read<String>(body, "$.data.inquiries[0].content"))
            assertEquals("비공개 답변입니다", JsonPath.read<String>(body, "$.data.inquiries[0].answer.content"))
            assertTrue(JsonPath.read<Boolean>(body, "$.data.inquiries[0].readable"))
        }
    }

    @Test
    fun `공개 문의는 다른 구매자에게도 본문이 보이고 작성자는 마스킹된다`() {
        ask(title = "제주 배송비", content = "제주 추가 배송비가 붙나요", secret = false).andExpect(status().isCreated)

        val body = listProductInquiries(asToken = otherBuyerToken)

        assertEquals("제주 배송비", JsonPath.read<String>(body, "$.data.inquiries[0].title"))
        assertEquals("제주 추가 배송비가 붙나요", JsonPath.read<String>(body, "$.data.inquiries[0].content"))
        assertEquals("김**", JsonPath.read<String>(body, "$.data.inquiries[0].authorMasked"))
        assertTrue(JsonPath.read<Boolean>(body, "$.data.inquiries[0].readable"))
    }

    @Test
    fun `철회하면 상품 목록과 문의 수에서 빠진다`() {
        val publicId = askAndReadPublicId()

        withdraw(publicId).andExpect(status().isNoContent)

        assertEquals(ProductInquiryStatus.WITHDRAWN, tx.execute { loadInquiry(publicId) }!!.status)
        val body = listProductInquiries(asToken = buyerToken)
        assertEquals(0, JsonPath.read<List<*>>(body, "$.data.inquiries").size)
        assertEquals(0, JsonPath.read<Int>(body, "$.data.totalCount"))
    }

    @Test
    fun `남의 문의는 철회할 수 없고 상태가 그대로 남는다`() {
        val publicId = askAndReadPublicId()

        withdraw(publicId, asToken = otherBuyerToken)
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.errorCode").value(120107))

        assertEquals(ProductInquiryStatus.RECEIVED, tx.execute { loadInquiry(publicId) }!!.status)
    }

    @Test
    fun `답변이 달린 문의는 철회할 수 없고 답변이 남는다`() {
        val publicId = askAndReadPublicId()
        tx.execute { loadInquiry(publicId)!!.writeAnswer(sellerUserId, "답변입니다", LocalDateTime.now()) }

        withdraw(publicId)
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.errorCode").value(120111))

        val stored =
            tx.execute {
                val inquiry = loadInquiry(publicId)!!
                Triple(inquiry.status, inquiry.answer?.content, inquiry.withdrawnAt)
            }!!
        assertEquals(ProductInquiryStatus.ANSWERED, stored.first)
        assertEquals("답변입니다", stored.second)
        assertNull(stored.third)
    }

    @Test
    fun `없는 문의를 철회하면 404다`() {
        withdraw("01NOTEXISTINQUIRYPUBLICID0").andExpect(status().isNotFound)
    }

    @Test
    fun `내 문의 목록은 내가 등록한 것만 돌려주고 철회한 것도 포함한다`() {
        val mine = askAndReadPublicId(title = "내 문의")
        val withdrawn = askAndReadPublicId(title = "철회할 문의")
        withdraw(withdrawn).andExpect(status().isNoContent)
        ask(title = "남의 문의", asToken = otherBuyerToken).andExpect(status().isCreated)

        val body = myInquiries()

        assertEquals(2, JsonPath.read<Int>(body, "$.data.totalCount"))
        assertEquals(
            listOf("철회할 문의", "내 문의"),
            JsonPath.read<List<String>>(body, "$.data.inquiries[*].title"),
        )
        assertEquals(productPublicId, JsonPath.read<String>(body, "$.data.inquiries[0].productId"))
        assertEquals(
            listOf(mine, withdrawn).sorted(),
            JsonPath.read<List<String>>(body, "$.data.inquiries[*].inquiryId").sorted(),
        )
    }

    @Test
    fun `내 문의 목록은 상태로 걸러진다`() {
        askAndReadPublicId(title = "대기 문의")
        val withdrawn = askAndReadPublicId(title = "철회 문의")
        withdraw(withdrawn).andExpect(status().isNoContent)

        val body = myInquiries(status = ProductInquiryStatus.WITHDRAWN)

        assertEquals(listOf("철회 문의"), JsonPath.read<List<String>>(body, "$.data.inquiries[*].title"))
    }

    private fun askSecretByOtherBuyerAndAnswer(): String {
        val publicId =
            askAndReadPublicId(
                title = "비밀 문의",
                content = "남에게 보이면 안 되는 내용",
                secret = true,
                asToken = otherBuyerToken,
            )
        tx.execute { loadInquiry(publicId)!!.writeAnswer(sellerUserId, "비공개 답변입니다", LocalDateTime.now()) }
        return publicId
    }

    private fun askAndReadPublicId(
        title: String = "크기 문의",
        content: String = "입구 높이가 몇 cm 인가요",
        secret: Boolean = false,
        asToken: String? = null,
    ): String {
        val body =
            ask(title = title, content = content, secret = secret, asToken = asToken)
                .andExpect(status().isCreated)
                .andReturn()
                .response
                .getContentAsString(Charsets.UTF_8)
        return JsonPath.read(body, "$.data.inquiryId")
    }

    private fun ask(
        title: String = "크기 문의",
        content: String = "입구 높이가 몇 cm 인가요",
        secret: Boolean = false,
        category: ProductInquiryCategory = ProductInquiryCategory.PRODUCT,
        asToken: String? = null,
    ): ResultActions =
        mockMvc.perform(
            post("/api/v1/products/$productPublicId/inquiries")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${asToken ?: buyerToken}")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"category":"${category.name}","title":"$title","content":"$content","secret":$secret}
                    """.trimIndent(),
                ),
        )

    private fun withdraw(
        inquiryPublicId: String,
        asToken: String? = null,
    ): ResultActions =
        mockMvc.perform(
            delete("/api/v1/product-inquiries/$inquiryPublicId")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${asToken ?: buyerToken}"),
        )

    private fun listProductInquiries(asToken: String): String =
        mockMvc
            .perform(
                get("/api/v1/products/$productPublicId/inquiries").header(HttpHeaders.AUTHORIZATION, "Bearer $asToken"),
            ).andExpect(status().isOk)
            .andReturn()
            .response
            .getContentAsString(Charsets.UTF_8)

    private fun myInquiries(status: ProductInquiryStatus? = null): String {
        val request = get("/api/v1/users/me/product-inquiries").header(HttpHeaders.AUTHORIZATION, "Bearer $buyerToken")
        status?.let { request.param("status", it.name) }
        return mockMvc
            .perform(request)
            .andExpect(status().isOk)
            .andReturn()
            .response
            .getContentAsString(Charsets.UTF_8)
    }

    private fun loadInquiry(publicId: String): ProductInquiry? =
        em
            .createQuery("select i from ProductInquiry i where i.publicId = :pid", ProductInquiry::class.java)
            .setParameter("pid", publicId)
            .resultList
            .firstOrNull()

    private fun countInquiries(): Long = em.createQuery("select count(i) from ProductInquiry i", Long::class.javaObjectType).singleResult

    private fun persistUser(nickname: String): Long {
        val user = User.preRegister()
        em.persist(user)
        em.flush()
        user.completeOnboarding(nickname)
        em.flush()
        return user.id
    }

    private fun persistVisibleProduct(name: String): Product {
        val root = Category.create(null, Category.ROOT_DEPTH, "강아지", null, 1)
        em.persist(root)
        val mid = Category.create(root, Category.MID_DEPTH, "식기", null, 1)
        em.persist(mid)
        em.persist(Seller.open(userId = sellerUserId, storeName = "애착스토어", baseShippingFee = 3000L))
        val product =
            Product.register(
                category = mid,
                sellerId = sellerUserId,
                name = name,
                description = null,
                representativeImageKey = "products/$name.jpg",
                regularPrice = 10000L,
                discountPrice = null,
                discountStartAt = null,
                discountEndAt = null,
            )
        em.persist(product)
        return product
    }
}
