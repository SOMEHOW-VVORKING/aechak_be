package com.aechak.api.product.search

import com.aechak.api.support.IntegrationTestBase
import com.aechak.application.auth.error.AuthErrorCode
import com.aechak.application.product.search.support.ProductKeywordSearchCursorCodec
import com.aechak.domain.product.category.Category
import com.aechak.domain.product.like.ProductLike
import com.aechak.domain.product.product.Product
import com.aechak.domain.product.product.enums.SaleStatus
import com.aechak.domain.product.stats.ProductStats
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
import org.springframework.security.web.FilterChainProxy
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * 키워드 상품 검색 API(GET /search/products) 통합 테스트
 */
class ProductKeywordSearchIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var context: WebApplicationContext

    @Autowired
    private lateinit var securityFilterChain: FilterChainProxy

    private lateinit var mockMvc: MockMvc
    private val defaultSellerId = 77L

    @BeforeEach
    fun setUp() {
        mockMvc =
            MockMvcBuilders
                .webAppContextSetup(context)
                .addFilters<DefaultMockMvcBuilder>(securityFilterChain)
                .build()
    }

    @Test
    fun `상품명에 키워드가 부분 일치하는 상품만 최신순으로 반환한다`() {
        tx.execute {
            val mid = persistMidCategory()
            persistProduct(mid, "강아지 사료")
            persistProduct(mid, "고양이 사료")
            persistProduct(mid, "강아지 장난감")
        }

        val body = search("사료")

        assertEquals(
            listOf("고양이 사료", "강아지 사료"), // id desc = 등록 역순, 장난감은 키워드 불일치로 제외
            JsonPath.read<List<String>>(body, "$.data.products[*].name"),
        )
        assertEquals(2, JsonPath.read<Int>(body, "$.data.totalCount"))
    }

    @Test
    fun `대소문자를 가리지 않고 매칭한다`() {
        tx.execute {
            val mid = persistMidCategory()
            persistProduct(mid, "iPhone Case")
        }

        val body = search("iphone")

        assertEquals(listOf("iPhone Case"), JsonPath.read<List<String>>(body, "$.data.products[*].name"))
    }

    @Test
    fun `판매중지 상품은 검색 결과에서 빠진다`() {
        tx.execute {
            val mid = persistMidCategory()
            persistProduct(mid, "사료 판매중")
            val suspended = persistProduct(mid, "사료 판매중지")
            em.flush()
            overrideSaleStatus(suspended.id, SaleStatus.SUSPENDED)
        }

        val body = search("사료")

        assertEquals(listOf("사료 판매중"), JsonPath.read<List<String>>(body, "$.data.products[*].name"))
        assertEquals(1, JsonPath.read<Int>(body, "$.data.totalCount"), "목록과 총개수 모두 노출 조건이 같아야 한다")
    }

    @Test
    fun `일치하는 상품이 없으면 빈 배열과 총개수 0을 반환한다`() {
        tx.execute {
            val mid = persistMidCategory()
            persistProduct(mid, "강아지 사료")
        }

        val body = search("없는키워드")

        assertEquals(0, JsonPath.read<List<*>>(body, "$.data.products").size)
        assertEquals(0, JsonPath.read<Int>(body, "$.data.totalCount"))
        assertFalse(JsonPath.read<Boolean>(body, "$.data.hasNext"))
        assertNull(JsonPath.read<String?>(body, "$.data.nextCursor"))
    }

    @Test
    fun `최신순 커서로 다음 페이지를 겹침 없이 끝까지 순회한다`() {
        tx.execute {
            val mid = persistMidCategory()
            (1..5).forEach { persistProduct(mid, "커서상품$it") }
        }

        val page1 = search("커서상품", size = 2)
        assertEquals(listOf("커서상품5", "커서상품4"), JsonPath.read<List<String>>(page1, "$.data.products[*].name"))
        assertTrue(JsonPath.read<Boolean>(page1, "$.data.hasNext"))

        val page2 = search("커서상품", size = 2, cursor = JsonPath.read<String>(page1, "$.data.nextCursor"))
        assertEquals(listOf("커서상품3", "커서상품2"), JsonPath.read<List<String>>(page2, "$.data.products[*].name"))

        val page3 = search("커서상품", size = 2, cursor = JsonPath.read<String>(page2, "$.data.nextCursor"))
        assertEquals(listOf("커서상품1"), JsonPath.read<List<String>>(page3, "$.data.products[*].name"))
        assertFalse(JsonPath.read<Boolean>(page3, "$.data.hasNext"))
        assertNull(JsonPath.read<String?>(page3, "$.data.nextCursor"))
    }

    @Test
    fun `총개수는 첫 페이지에만 실리고 이후 페이지는 null이다`() {
        tx.execute {
            val mid = persistMidCategory()
            (1..3).forEach { persistProduct(mid, "커서상품$it") }
        }

        val page1 = search("커서상품", size = 2)
        assertEquals(3, JsonPath.read<Int>(page1, "$.data.totalCount"))

        val page2 = search("커서상품", size = 2, cursor = JsonPath.read<String>(page1, "$.data.nextCursor"))
        assertNull(JsonPath.read<Any?>(page2, "$.data.totalCount"))
    }

    @Test
    fun `다른 검색어에서 받은 커서는 400으로 거절한다`() {
        tx.execute {
            val mid = persistMidCategory()
            (1..3).forEach { persistProduct(mid, "강아지 사료$it") }
        }

        val cursorForSaryo = JsonPath.read<String>(search("사료", size = 1), "$.data.nextCursor")

        mockMvc
            .perform(get("/api/v1/search/products").param("keyword", "강아지").param("cursor", cursorForSaryo))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `깨진 커서는 400으로 거절한다`() {
        mockMvc
            .perform(get("/api/v1/search/products").param("keyword", "사료").param("cursor", "%%%깨진커서%%%"))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `검색어가 없거나 공백이면 400을 반환한다`() {
        mockMvc.perform(get("/api/v1/search/products")).andExpect(status().isBadRequest)
        mockMvc.perform(get("/api/v1/search/products").param("keyword", " ")).andExpect(status().isBadRequest)
        // 전각 공백(U+3000)과 NBSP(U+00A0)는 @NotBlank를 통과하지만 코틀린 기준 공백이므로 400 응답 필요
        mockMvc.perform(get("/api/v1/search/products").param("keyword", "\u3000")).andExpect(status().isBadRequest)
        mockMvc.perform(get("/api/v1/search/products").param("keyword", "\u00A0")).andExpect(status().isBadRequest)
    }

    @Test
    fun `size가 범위를 벗어나면 400을 반환한다`() {
        mockMvc
            .perform(get("/api/v1/search/products").param("keyword", "사료").param("size", "0"))
            .andExpect(status().isBadRequest)
        mockMvc
            .perform(get("/api/v1/search/products").param("keyword", "사료").param("size", "101"))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `카드는 셀러명과 응답 시각 기준 할인, 별점을 담는다`() {
        tx.execute {
            val now = LocalDateTime.now()
            persistSeller(defaultSellerId, storeName = "멍멍상회")
            val mid = persistMidCategory()
            val product =
                persistProduct(
                    mid,
                    "강아지 사료",
                    regular = 20000L,
                    discount = 14900L,
                    start = now.minusHours(1),
                    end = now.plusHours(1),
                )
            em.flush()
            persistStats(product.id, reviewCount = 12, averageRating = BigDecimal("4.50"))
        }

        val body = search("사료")

        assertEquals("멍멍상회", JsonPath.read<String>(body, "$.data.products[0].sellerName"))
        assertEquals(14900, JsonPath.read<Int>(body, "$.data.products[0].discountPrice"))
        assertEquals(26, JsonPath.read<Int>(body, "$.data.products[0].discountRate")) // 25.5% 반올림
        assertEquals(12, JsonPath.read<Int>(body, "$.data.products[0].reviewCount"))
        assertEquals(
            0,
            BigDecimal("4.50").compareTo(
                BigDecimal(
                    JsonPath.read<Any>(body, "$.data.products[0].averageRating").toString(),
                ),
            ),
            "별점이 응답에 매핑돼야 한다",
        )
        assertFalse(JsonPath.read<Boolean>(body, "$.data.products[0].isLiked"), "게스트 검색 카드의 isLiked는 false다")
    }

    @Test
    fun `로그인 사용자의 검색 카드는 찜한 상품만 isLiked로 반환한다`() {
        val userId = createActiveUser()
        tx.execute {
            val mid = persistMidCategory()
            val liked = persistProduct(mid, "강아지 사료 찜함")
            persistProduct(mid, "강아지 사료 안함")
            em.flush()
            em.persist(ProductLike.of(liked, userId))
        }

        val body = searchAuth("사료", mintAccessToken(userId))

        val likedByName =
            JsonPath
                .read<List<String>>(body, "$.data.products[*].name")
                .zip(JsonPath.read<List<Boolean>>(body, "$.data.products[*].isLiked"))
                .toMap()
        assertTrue(likedByName.getValue("강아지 사료 찜함"))
        assertFalse(likedByName.getValue("강아지 사료 안함"))
    }

    @Test
    fun `특수문자는 와일드카드가 아니라 리터럴로 매칭된다`() {
        tx.execute {
            val mid = persistMidCategory()
            persistProduct(mid, "10%적립 사료")
            persistProduct(mid, "10원적립 사료")
            persistProduct(mid, "a_b 사료")
            persistProduct(mid, "aXb 사료")
            persistProduct(mid, "back\\slash 사료")
        }

        assertEquals(listOf("10%적립 사료"), JsonPath.read<List<String>>(search("10%"), "$.data.products[*].name"))
        assertEquals(listOf("a_b 사료"), JsonPath.read<List<String>>(search("a_b"), "$.data.products[*].name"))
        assertEquals(
            listOf("back\\slash 사료"),
            JsonPath.read<List<String>>(search("back\\slash"), "$.data.products[*].name"),
        )
    }

    @Test
    fun `결과 수가 정확히 size면 hasNext는 false다`() {
        tx.execute {
            val mid = persistMidCategory()
            (1..2).forEach { persistProduct(mid, "딱맞음$it") }
        }

        val body = search("딱맞음", size = 2)

        assertEquals(2, JsonPath.read<List<*>>(body, "$.data.products").size)
        assertFalse(JsonPath.read<Boolean>(body, "$.data.hasNext"), "size와 결과 수가 같으면 다음 페이지가 없다")
        assertNull(JsonPath.read<String?>(body, "$.data.nextCursor"))
    }

    @Test
    fun `존재하지 않는 publicId를 담은 커서는 400으로 거절한다`() {
        tx.execute {
            val mid = persistMidCategory()
            persistProduct(mid, "강아지 사료")
        }

        val forged = ProductKeywordSearchCursorCodec.encode("사료", "00000000000000000000000000")

        mockMvc
            .perform(get("/api/v1/search/products").param("keyword", "사료").param("cursor", forged))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `검색어 정규화 후 같은 커서를 이어서 쓸 수 있다`() {
        tx.execute {
            val mid = persistMidCategory()
            (1..3).forEach { persistProduct(mid, "iPhone $it") }
        }

        val page1 = search("  IPHONE  ", size = 2)
        assertEquals(listOf("iPhone 3", "iPhone 2"), JsonPath.read<List<String>>(page1, "$.data.products[*].name"))

        val page2 = search("iphone", size = 2, cursor = JsonPath.read<String>(page1, "$.data.nextCursor"))
        assertEquals(listOf("iPhone 1"), JsonPath.read<List<String>>(page2, "$.data.products[*].name"), "겹침 없이 다음 한 건")
        val walked =
            JsonPath.read<List<String>>(page1, "$.data.products[*].name") +
                JsonPath.read<List<String>>(page2, "$.data.products[*].name")
        assertEquals(walked, walked.distinct(), "누락도 중복도 없이 이어진다")
    }

    @Test
    fun `온보딩 대기 유저는 403으로 검색이 막힌다`() {
        // 게스트(토큰 없음)는 permitAll로 검색되지만, 온보딩 미완료(PENDING) 유저는 허용 목록에서 빠져 UserStatusFilter가 막는다.
        val pendingUserId =
            tx.execute {
                val user = User.preRegister()
                em.persist(user)
                em.flush()
                user.id
            }!!
        val token = mintAccessToken(pendingUserId)

        mockMvc
            .perform(
                get("/api/v1/search/products")
                    .param("keyword", "사료")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $token"),
            ).andExpect(status().isForbidden)
            .andExpect(jsonPath("$.errorCode").value(AuthErrorCode.ONBOARDING_REQUIRED.code))
    }

    // --- helpers ---

    private fun search(
        keyword: String,
        cursor: String? = null,
        size: Int? = null,
    ): String {
        val request = get("/api/v1/search/products").param("keyword", keyword)
        cursor?.let { request.param("cursor", it) }
        size?.let { request.param("size", it.toString()) }
        return mockMvc
            .perform(request)
            .andExpect(status().isOk)
            .andReturn()
            .response
            .getContentAsString(Charsets.UTF_8)
    }

    private fun searchAuth(
        keyword: String,
        token: String,
    ): String =
        mockMvc
            .perform(
                get("/api/v1/search/products")
                    .param("keyword", keyword)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $token"),
            ).andExpect(status().isOk)
            .andReturn()
            .response
            .getContentAsString(Charsets.UTF_8)

    // ---------- 픽스처 (tx.execute 안에서만 호출) ----------

    private fun persistMidCategory(name: String = "사료간식"): Category {
        val root = Category.create(null, 1, "강아지", null, 1)
        em.persist(root)
        val mid = Category.create(root, 2, name, null, 1)
        em.persist(mid)
        return mid
    }

    private fun persistProduct(
        category: Category,
        name: String,
        regular: Long = 10000L,
        discount: Long? = null,
        start: LocalDateTime? = null,
        end: LocalDateTime? = null,
        sellerId: Long = defaultSellerId,
    ): Product {
        ensureActiveSeller(sellerId)
        val product =
            Product.register(
                category = category,
                sellerId = sellerId,
                name = name,
                description = null,
                representativeImageKey = "products/$name.jpg",
                regularPrice = regular,
                discountPrice = discount,
                discountStartAt = start,
                discountEndAt = end,
            )
        em.persist(product)
        return product
    }

    /** 기본 ACTIVE 셀러 조회 또는 생성 */
    private fun ensureActiveSeller(sellerId: Long) {
        if (em.find(Seller::class.java, sellerId) == null) {
            em.persist(Seller.open(userId = sellerId, storeName = "store-$sellerId", baseShippingFee = 3000L))
        }
    }

    private fun persistSeller(
        sellerId: Long,
        storeName: String,
    ) {
        em.persist(Seller.open(userId = sellerId, storeName = storeName, baseShippingFee = 3000L))
    }

    /** 상태 세터가 없어 flush 후 bulk update */
    private fun overrideSaleStatus(
        productId: Long,
        status: SaleStatus,
    ) {
        em
            .createQuery("update Product p set p.saleStatus = :status where p.id = :id")
            .setParameter("status", status)
            .setParameter("id", productId)
            .executeUpdate()
    }

    private fun persistStats(
        productId: Long,
        reviewCount: Int,
        averageRating: BigDecimal,
    ) {
        em.persist(ProductStats.init(productId))
        em.flush()
        em
            .createQuery(
                "update ProductStats s set s.reviewCount = :reviewCount, s.averageRating = :averageRating where s.productId = :id",
            ).setParameter("reviewCount", reviewCount)
            .setParameter("averageRating", averageRating)
            .setParameter("id", productId)
            .executeUpdate()
    }
}
