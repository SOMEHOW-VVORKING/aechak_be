package com.aechak.api.product.search

import com.aechak.api.support.IntegrationTestBase
import com.aechak.domain.product.category.Category
import com.aechak.domain.product.category.enums.CategoryStatus
import com.aechak.domain.product.product.Product
import com.aechak.domain.product.stats.ProductStats
import com.aechak.domain.seller.seller.Seller
import com.jayway.jsonpath.JsonPath
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.web.FilterChainProxy
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.math.BigDecimal
import java.text.Normalizer
import java.time.Duration
import java.time.LocalDateTime

/**
 * 키워드 상품 검색의 상품명 일치, 노출 카테고리, 커서 순회 의미를 고정하는 통합 테스트
 */
class ProductKeywordSearchSemanticsIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var context: WebApplicationContext

    @Autowired
    private lateinit var securityFilterChain: FilterChainProxy

    private lateinit var mockMvc: MockMvc
    private val sellerId = 77L

    @BeforeEach
    fun setUp() {
        mockMvc =
            MockMvcBuilders
                .webAppContextSetup(context)
                .addFilters<DefaultMockMvcBuilder>(securityFilterChain)
                .build()
    }

    @Test
    fun `1글자 검색어는 상품명 어디에 있든 부분 일치한다`() {
        tx.execute {
            val mid = persistMidCategory()
            persistProduct(mid, "개껌")
            persistProduct(mid, "강아지 개 목줄")
            persistProduct(mid, "고양이 사료")
        }

        val body = search("개")

        assertEquals(listOf("강아지 개 목줄", "개껌"), productNames(body))
        assertEquals(2, totalCount(body))
    }

    @Test
    fun `내부 공백이 있는 검색어는 공백까지 그대로 연속 일치해야 한다`() {
        tx.execute {
            val mid = persistMidCategory()
            persistProduct(mid, "프리미엄 강아지 사료 2kg")
            persistProduct(mid, "강아지사료")
            persistProduct(mid, "강아지  사료")
            persistProduct(mid, "사료 강아지")
        }

        val body = search("강아지 사료")

        assertEquals(listOf("프리미엄 강아지 사료 2kg"), productNames(body))
        assertEquals(1, totalCount(body))
    }

    @Test
    fun `NFD 검색어는 NFC로 정규화되어 NFC 입력과 같은 결과를 낸다`() {
        tx.execute {
            val mid = persistMidCategory()
            persistProduct(mid, "강아지 사료")
            persistProduct(mid, "고양이 사료")
        }
        val nfd = Normalizer.normalize("강아지", Normalizer.Form.NFD)
        assertFalse(nfd == "강아지", "NFD 입력은 NFC와 다른 문자열이어야 한다")

        val nfcBody = search("강아지")
        val nfdBody = search(nfd)

        assertEquals(listOf("강아지 사료"), productNames(nfcBody))
        assertEquals(productNames(nfcBody), productNames(nfdBody))
        assertEquals(totalCount(nfcBody), totalCount(nfdBody))
    }

    @Test
    fun `NFD로 저장된 상품명은 NFC 검색어로 찾지 못한다`() {
        // 상품명은 저장 시 정규화하지 않고 LIKE는 자모 분해형과 완성형을 같은 글자로 보지 않음
        tx.execute {
            val mid = persistMidCategory()
            persistProduct(mid, Normalizer.normalize("강아지 사료", Normalizer.Form.NFD))
        }

        val body = search("강아지")

        assertEquals(emptyList<String>(), productNames(body))
        assertEquals(0, totalCount(body))
    }

    @Test
    fun `100자 검색어는 허용되어 일치하고 101자는 400이다`() {
        val keyword = "가나다라마바사아자차".repeat(10)
        assertEquals(100, keyword.length)
        tx.execute {
            val mid = persistMidCategory()
            persistProduct(mid, "프리미엄 $keyword 세트")
            persistProduct(mid, "프리미엄 ${keyword.dropLast(1)} 세트")
        }

        val body = search(keyword)

        assertEquals(listOf("프리미엄 $keyword 세트"), productNames(body))
        assertEquals(1, totalCount(body))
        mockMvc
            .perform(get("/api/v1/search/products").param("keyword", keyword + "가"))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `비활성 중분류나 대분류 아래 상품은 목록과 총개수에서 빠진다`() {
        tx.execute {
            val activeRoot = persistCategory(null, 1, "강아지")
            val activeMid = persistCategory(activeRoot, 2, "사료")
            persistProduct(activeMid, "노출 중분류직속 사료")
            persistProduct(persistCategory(activeMid, 3, "건식"), "노출 소분류 사료")
            val deletedLeaf = persistCategory(activeMid, 3, "단종")
            persistProduct(deletedLeaf, "비활성 소분류 사료")

            val inactiveMid = persistCategory(activeRoot, 2, "간식")
            persistProduct(inactiveMid, "비활성 중분류직속 사료")
            persistProduct(persistCategory(inactiveMid, 3, "져키"), "비활성 중분류 아래 소분류 사료")

            val inactiveRoot = persistCategory(null, 1, "고양이")
            val midUnderInactiveRoot = persistCategory(inactiveRoot, 2, "사료")
            persistProduct(midUnderInactiveRoot, "비활성 대분류 아래 중분류직속 사료")
            persistProduct(persistCategory(midUnderInactiveRoot, 3, "습식"), "비활성 대분류 아래 소분류 사료")
            em.flush()

            overrideCategoryStatus(inactiveMid.id, CategoryStatus.INACTIVE)
            overrideCategoryStatus(inactiveRoot.id, CategoryStatus.INACTIVE)
            overrideCategoryStatus(deletedLeaf.id, CategoryStatus.DELETED)
        }

        val body = search("사료")

        assertEquals(listOf("노출 소분류 사료", "노출 중분류직속 사료"), productNames(body))
        assertEquals(2, totalCount(body))
    }

    @Test
    fun `중분류 필터는 그 중분류 직속과 활성 소분류 상품만 남긴다`() {
        val midId =
            tx.execute {
                val root = persistCategory(null, 1, "강아지")
                val mid = persistCategory(root, 2, "사료")
                persistProduct(mid, "필터 중분류직속 사료")
                persistProduct(persistCategory(mid, 3, "건식"), "필터 소분류 사료")
                val inactiveLeaf = persistCategory(mid, 3, "단종")
                persistProduct(inactiveLeaf, "필터 비활성 소분류 사료")
                persistProduct(persistCategory(root, 2, "간식"), "필터 다른 중분류 사료")
                em.flush()
                overrideCategoryStatus(inactiveLeaf.id, CategoryStatus.INACTIVE)
                mid.id
            }!!

        val body = search("사료", category = midId)

        assertEquals(listOf("필터 소분류 사료", "필터 중분류직속 사료"), productNames(body))
        assertEquals(2, totalCount(body))
    }

    @Test
    fun `대분류가 비활성인 활성 중분류로 필터하면 빈 결과를 반환한다`() {
        val midId =
            tx.execute {
                val root = persistCategory(null, 1, "고양이")
                val mid = persistCategory(root, 2, "사료")
                persistProduct(mid, "고양이 사료")
                persistProduct(persistCategory(mid, 3, "습식"), "고양이 습식 사료")
                em.flush()
                overrideCategoryStatus(root.id, CategoryStatus.INACTIVE)
                mid.id
            }!!

        val body = search("사료", category = midId)

        assertEquals(emptyList<String>(), productNames(body))
        assertEquals(0, totalCount(body))
    }

    @Test
    fun `낮은가격순 커서는 유효가격과 id 역순 동률 정렬로 끝까지 순회한다`() {
        val now = LocalDateTime.now()
        tx.execute {
            val mid = persistMidCategory()
            persistProduct(mid, "가격 사료 A", regular = 30000L)
            persistProduct(mid, "가격 사료 B", regular = 20000L)
            persistProduct(mid, "가격 사료 C", regular = 50000L, discount = 20000L, start = now.minusDays(1))
            persistProduct(mid, "가격 사료 D", regular = 10000L)
            persistProduct(mid, "가격 사료 E", regular = 40000L, discount = 5000L, start = now.minusDays(2), end = now.minusDays(1))
        }

        val walked = mutableListOf<String>()
        var cursor: String? = null
        var pages = 0
        do {
            val body = search("사료", sort = "price_asc", size = 2, cursor = cursor)
            walked += productNames(body)
            cursor = JsonPath.read<String?>(body, "$.data.nextCursor")
            pages++
        } while (cursor != null)

        // C(할인 20000)와 B(20000)는 동률이라 id 역순, E는 지난 할인이라 정가 40000
        assertEquals(listOf("가격 사료 D", "가격 사료 C", "가격 사료 B", "가격 사료 A", "가격 사료 E"), walked)
        assertEquals(3, pages)
    }

    @Test
    fun `페이지 사이에 할인이 시작되어도 낮은가격순 순회는 첫 페이지 시각 가격으로 이어진다`() {
        val boundary = LocalDateTime.now().plusSeconds(2)
        tx.execute {
            val mid = persistMidCategory()
            persistProduct(mid, "경계 사료 A", regular = 10000L)
            persistProduct(mid, "경계 사료 B", regular = 20000L)
            persistProduct(mid, "경계 사료 C", regular = 30000L, discount = 5000L, start = boundary)
            persistProduct(mid, "경계 사료 D", regular = 40000L)
        }

        val page1 = search("사료", sort = "price_asc", size = 2)
        assertTrue(LocalDateTime.now().isBefore(boundary), "첫 페이지는 할인 시작 전에 받아야 한다")
        assertEquals(listOf("경계 사료 A", "경계 사료 B"), productNames(page1))

        await().atMost(Duration.ofSeconds(5)).until { LocalDateTime.now().isAfter(boundary) }

        val page2 = search("사료", sort = "price_asc", size = 2, cursor = JsonPath.read(page1, "$.data.nextCursor"))

        assertEquals(listOf("경계 사료 C", "경계 사료 D"), productNames(page2), "할인가 5000으로 앞쪽에 밀려 누락되지 않는다")
        assertFalse(JsonPath.read<Boolean>(page2, "$.data.hasNext"))
        assertNull(JsonPath.read<String?>(page2, "$.data.nextCursor"))
        assertEquals(5000, JsonPath.read<Int>(page2, "$.data.products[0].discountPrice"), "카드 표시가는 현재 시각 기준")
    }

    @Test
    fun `최소 평점은 경계값을 포함하고 리뷰 없는 상품은 0점 하한에서도 빠진다`() {
        tx.execute {
            val mid = persistMidCategory()
            persistProductWithRating(mid, "평점 사료 399", BigDecimal("3.99"))
            persistProductWithRating(mid, "평점 사료 400", BigDecimal("4.00"))
            persistProductWithRating(mid, "평점 사료 401", BigDecimal("4.01"))
            persistProductWithRating(mid, "평점 사료 500", BigDecimal("5.00"))
            persistProductWithRating(mid, "평점 사료 000", BigDecimal("0.00"))
            persistProduct(mid, "평점 사료 없음")
        }

        val atFour = search("사료", minRating = "4.0", sort = "latest")
        assertEquals(listOf("평점 사료 500", "평점 사료 401", "평점 사료 400"), productNames(atFour))
        assertEquals(3, totalCount(atFour))

        assertEquals(listOf("평점 사료 500", "평점 사료 401"), productNames(search("사료", minRating = "4.01", sort = "latest")))
        assertEquals(listOf("평점 사료 500"), productNames(search("사료", minRating = "5.0", sort = "latest")))

        val atZero = search("사료", minRating = "0", sort = "latest")
        assertEquals(5, totalCount(atZero), "리뷰 없는 상품만 빠진다")
        assertFalse(productNames(atZero).contains("평점 사료 없음"))
    }

    // --- helpers ---

    private fun search(
        keyword: String,
        cursor: String? = null,
        size: Int? = null,
        sort: String? = null,
        minRating: String? = null,
        category: Long? = null,
    ): String {
        val request = get("/api/v1/search/products").param("keyword", keyword)
        cursor?.let { request.param("cursor", it) }
        size?.let { request.param("size", it.toString()) }
        sort?.let { request.param("sort", it) }
        minRating?.let { request.param("minRating", it) }
        category?.let { request.param("category", it.toString()) }
        return mockMvc
            .perform(request)
            .andExpect(status().isOk)
            .andReturn()
            .response
            .getContentAsString(Charsets.UTF_8)
    }

    private fun productNames(body: String): List<String> = JsonPath.read(body, "$.data.products[*].name")

    private fun totalCount(body: String): Int = JsonPath.read(body, "$.data.totalCount")

    // ---------- 픽스처 (tx.execute 안에서만 호출) ----------

    private fun persistCategory(
        parent: Category?,
        depth: Int,
        name: String,
    ): Category = Category.create(parent, depth, name, null, 1).also { em.persist(it) }

    private fun persistMidCategory(): Category = persistCategory(persistCategory(null, 1, "강아지"), 2, "사료간식")

    private fun persistProduct(
        category: Category,
        name: String,
        regular: Long = 10000L,
        discount: Long? = null,
        start: LocalDateTime? = null,
        end: LocalDateTime? = null,
    ): Product {
        if (em.find(Seller::class.java, sellerId) == null) {
            em.persist(Seller.open(userId = sellerId, storeName = "store-$sellerId", baseShippingFee = 3000L))
        }
        val product =
            Product.register(
                category = category,
                sellerId = sellerId,
                name = name,
                description = null,
                representativeImageKey = "products/${name.hashCode()}.jpg",
                regularPrice = regular,
                discountPrice = discount,
                discountStartAt = start,
                discountEndAt = end,
            )
        em.persist(product)
        return product
    }

    private fun persistProductWithRating(
        category: Category,
        name: String,
        averageRating: BigDecimal,
    ) {
        val product = persistProduct(category, name)
        em.flush()
        em.persist(ProductStats.create(product.id))
        em.flush()
        em
            .createQuery("update ProductStats s set s.reviewCount = 1, s.averageRating = :averageRating where s.productId = :id")
            .setParameter("averageRating", averageRating)
            .setParameter("id", product.id)
            .executeUpdate()
    }

    /** 상태 세터가 없어 flush 후 bulk update */
    private fun overrideCategoryStatus(
        categoryId: Long,
        status: CategoryStatus,
    ) {
        em
            .createQuery("update Category c set c.status = :status where c.id = :id")
            .setParameter("status", status)
            .setParameter("id", categoryId)
            .executeUpdate()
    }
}
