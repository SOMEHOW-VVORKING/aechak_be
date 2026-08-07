package com.aechak.api.product.search

import com.aechak.api.support.IntegrationTestBase
import com.aechak.application.auth.error.AuthErrorCode
import com.aechak.application.product.search.port.ProductKeywordFilter
import com.aechak.application.product.search.port.ProductKeywordSearchSort
import com.aechak.application.product.search.support.ProductKeywordSearchCursorCodec
import com.aechak.domain.product.category.Category
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

        val forged =
            ProductKeywordSearchCursorCodec.encode(
                sort = ProductKeywordSearchSort.POPULAR,
                filterHash = ProductKeywordSearchCursorCodec.filterHash(filterOf("사료")),
                publicId = "00000000000000000000000000",
                lastReviewCount = 0,
                lastPrice = null,
                now = LocalDateTime.now(),
            )

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

    // ---------- SCRUM-158 정렬 ----------

    @Test
    fun `인기순은 리뷰 수 내림차순으로 정렬한다`() {
        tx.execute {
            val mid = persistMidCategory()
            val low = persistProduct(mid, "정렬 사료 A")
            em.flush()
            persistStats(low.id, reviewCount = 3, averageRating = BigDecimal("3.00"))
            val high = persistProduct(mid, "정렬 사료 B")
            em.flush()
            persistStats(high.id, reviewCount = 50, averageRating = BigDecimal("2.00"))
            persistProduct(mid, "정렬 사료 C") // 통계 없음 = 리뷰 수 0
        }

        val body = search("사료", sort = "popular")

        assertEquals(listOf("정렬 사료 B", "정렬 사료 A", "정렬 사료 C"), productNames(body), "리뷰 수 desc, 통계 없는 상품은 0으로 뒤")
    }

    @Test
    fun `낮은가격순은 유효가격 오름차순으로 정렬한다`() {
        tx.execute {
            val mid = persistMidCategory()
            persistProduct(mid, "가격 사료 A", regular = 30000L)
            persistProduct(mid, "가격 사료 B", regular = 10000L)
            persistProduct(mid, "가격 사료 C", regular = 20000L)
        }

        val body = search("사료", sort = "price_asc")

        assertEquals(listOf("가격 사료 B", "가격 사료 C", "가격 사료 A"), productNames(body))
    }

    @Test
    fun `최신순은 id 내림차순으로 정렬한다`() {
        tx.execute {
            val mid = persistMidCategory()
            (1..3).forEach { persistProduct(mid, "최신 사료 $it") }
        }

        val body = search("사료", sort = "latest")

        assertEquals(listOf("최신 사료 3", "최신 사료 2", "최신 사료 1"), productNames(body))
    }

    @Test
    fun `인기순 커서로 다음 페이지를 겹침 없이 순회한다`() {
        tx.execute {
            val mid = persistMidCategory()
            (1..4).forEach {
                val p = persistProduct(mid, "인기 사료 $it")
                em.flush()
                persistStats(p.id, reviewCount = it * 10, averageRating = BigDecimal("3.00"))
            }
        }

        val page1 = search("사료", sort = "popular", size = 2)
        assertEquals(listOf("인기 사료 4", "인기 사료 3"), productNames(page1))

        val page2 = search("사료", sort = "popular", size = 2, cursor = JsonPath.read(page1, "$.data.nextCursor"))
        assertEquals(listOf("인기 사료 2", "인기 사료 1"), productNames(page2))
        assertFalse(JsonPath.read<Boolean>(page2, "$.data.hasNext"))
    }

    // ---------- SCRUM-158 필터 ----------

    @Test
    fun `가격대 필터는 유효가격 범위 안의 상품만 남긴다`() {
        tx.execute {
            val mid = persistMidCategory()
            persistProduct(mid, "가격 사료 5천", regular = 5000L)
            persistProduct(mid, "가격 사료 3만", regular = 30000L)
            persistProduct(mid, "가격 사료 5만", regular = 50000L)
        }

        val body = search("사료", minPrice = 10000L, maxPrice = 40000L, sort = "price_asc")

        assertEquals(listOf("가격 사료 3만"), productNames(body))
        assertEquals(1, JsonPath.read<Int>(body, "$.data.totalCount"), "총개수도 같은 필터를 반영한다")
    }

    @Test
    fun `최소 평점 필터는 별점 하한 이상만 남기고 리뷰 없는 상품은 제외한다`() {
        tx.execute {
            val mid = persistMidCategory()
            val a = persistProduct(mid, "평점 사료 A")
            em.flush()
            persistStats(a.id, reviewCount = 10, averageRating = BigDecimal("4.50"))
            val b = persistProduct(mid, "평점 사료 B")
            em.flush()
            persistStats(b.id, reviewCount = 10, averageRating = BigDecimal("3.00"))
            persistProduct(mid, "평점 사료 C") // 리뷰 없음 = averageRating null
        }

        val body = search("사료", minRating = "4.0")

        assertEquals(listOf("평점 사료 A"), productNames(body))
        assertEquals(1, JsonPath.read<Int>(body, "$.data.totalCount"))
    }

    @Test
    fun `무료배송 필터는 셀러 기본 배송비가 0인 상품만 남긴다`() {
        tx.execute {
            val mid = persistMidCategory()
            persistSellerWithFee(101L, "무료셀러", 0L)
            persistSellerWithFee(102L, "유료셀러", 3000L)
            persistProduct(mid, "무배 사료 A", sellerId = 101L)
            persistProduct(mid, "무배 사료 B", sellerId = 102L)
        }

        val body = search("사료", freeShipping = true)

        assertEquals(listOf("무배 사료 A"), productNames(body))
    }

    @Test
    fun `품절 제외 필터는 판매중만 남긴다`() {
        tx.execute {
            val mid = persistMidCategory()
            persistProduct(mid, "재고 사료 판매중")
            val oos = persistProduct(mid, "재고 사료 품절")
            em.flush()
            overrideSaleStatus(oos.id, SaleStatus.OUT_OF_STOCK)
        }

        assertEquals(
            listOf("재고 사료 품절", "재고 사료 판매중"),
            productNames(search("사료", sort = "latest")),
            "기본은 품절도 노출(최신순 id desc라 나중에 등록한 품절이 앞)",
        )
        assertEquals(listOf("재고 사료 판매중"), productNames(search("사료", excludeSoldOut = true)))
    }

    @Test
    fun `카테고리 필터는 지정한 중분류의 상품만 남긴다`() {
        val midId =
            tx.execute {
                val mid1 = persistMidCategory("사료류")
                val mid2 = persistMidCategory("장난감류")
                persistProduct(mid1, "카테고리 사료 A")
                persistProduct(mid2, "카테고리 사료 B")
                mid1.id
            }!!

        val body = search("사료", category = midId)

        assertEquals(listOf("카테고리 사료 A"), productNames(body))
    }

    @Test
    fun `대분류로 카테고리 필터하면 400을 반환한다`() {
        val rootId =
            tx.execute {
                val root = Category.create(null, 1, "강아지", null, 1)
                em.persist(root)
                em.flush()
                root.id
            }!!

        mockMvc
            .perform(get("/api/v1/search/products").param("keyword", "사료").param("category", rootId.toString()))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `없는 카테고리로 필터하면 404를 반환한다`() {
        mockMvc
            .perform(get("/api/v1/search/products").param("keyword", "사료").param("category", "999999"))
            .andExpect(status().isNotFound)
    }

    // ---------- SCRUM-158 커서와 검증 ----------

    @Test
    fun `다른 정렬에서 받은 커서는 400으로 거절한다`() {
        tx.execute {
            val mid = persistMidCategory()
            (1..3).forEach { persistProduct(mid, "정렬커서 사료 $it") }
        }

        val popularCursor = JsonPath.read<String>(search("사료", sort = "popular", size = 1), "$.data.nextCursor")

        mockMvc
            .perform(
                get("/api/v1/search/products")
                    .param("keyword", "사료")
                    .param("sort", "price_asc")
                    .param("cursor", popularCursor),
            ).andExpect(status().isBadRequest)
    }

    @Test
    fun `다른 필터에서 받은 커서는 400으로 거절한다`() {
        tx.execute {
            val mid = persistMidCategory()
            (1..3).forEach { persistProduct(mid, "필터커서 사료 $it", regular = (it * 10000).toLong()) }
        }

        val cursor = JsonPath.read<String>(search("사료", size = 1, minPrice = 5000L), "$.data.nextCursor")

        mockMvc
            .perform(
                get("/api/v1/search/products")
                    .param("keyword", "사료")
                    .param("minPrice", "20000")
                    .param("cursor", cursor),
            ).andExpect(status().isBadRequest)
    }

    @Test
    fun `가격이나 별점 검증에 걸리면 400을 반환한다`() {
        mockMvc
            .perform(get("/api/v1/search/products").param("keyword", "사료").param("minPrice", "50000").param("maxPrice", "10000"))
            .andExpect(status().isBadRequest)
        mockMvc
            .perform(get("/api/v1/search/products").param("keyword", "사료").param("minPrice", "-1"))
            .andExpect(status().isBadRequest)
        mockMvc
            .perform(get("/api/v1/search/products").param("keyword", "사료").param("minRating", "5.5"))
            .andExpect(status().isBadRequest)
        mockMvc
            .perform(get("/api/v1/search/products").param("keyword", "사료").param("sort", "unknown"))
            .andExpect(status().isBadRequest)
        // 큰 소수 자릿수는 @Digits로 400 (메모리 고갈 방지)
        mockMvc
            .perform(get("/api/v1/search/products").param("keyword", "사료").param("minRating", "0.001"))
            .andExpect(status().isBadRequest)
        mockMvc
            .perform(get("/api/v1/search/products").param("keyword", "사료").param("minRating", "1E-100000000"))
            .andExpect(status().isBadRequest)
    }

    // ---------- SCRUM-159 최근 검색어 적재 ----------

    @Test
    fun `로그인 사용자가 검색하면 최근 검색어로 적재된다`() {
        val userId = createActiveUser()
        tx.execute {
            val mid = persistMidCategory()
            persistProduct(mid, "강아지 사료")
        }

        searchAsUser("사료", mintAccessToken(userId))

        assertEquals(listOf("사료"), recentKeywords(userId))
    }

    @Test
    fun `게스트 검색은 적재되지 않는다`() {
        tx.execute {
            val mid = persistMidCategory()
            persistProduct(mid, "강아지 사료")
        }

        search("사료")

        assertEquals(0L, countAllRecent(), "비로그인 검색은 recent_searches에 남지 않는다")
    }

    @Test
    fun `같은 키워드 재검색은 새 행 없이 최신으로 갱신한다`() {
        val userId = createActiveUser()
        val token = mintAccessToken(userId)
        tx.execute {
            val mid = persistMidCategory()
            persistProduct(mid, "강아지 사료")
        }

        searchAsUser("사료", token)
        searchAsUser("사료", token)

        assertEquals(1L, countRecent(userId), "원자적 upsert라 중복 행이 생기지 않는다")
        assertEquals(listOf("사료"), recentKeywords(userId))
    }

    @Test
    fun `대소문자가 다른 키워드는 각각 적재된다`() {
        val userId = createActiveUser()
        val token = mintAccessToken(userId)
        tx.execute {
            val mid = persistMidCategory()
            persistProduct(mid, "iPhone Case")
        }

        searchAsUser("iPhone", token)
        searchAsUser("iphone", token)

        assertEquals(2L, countRecent(userId), "as_cs 콜레이션이라 대소문자가 다르면 각각 저장된다")
        assertTrue(recentKeywords(userId).containsAll(listOf("iPhone", "iphone")), "친 표기가 각각 보존된다")
    }

    @Test
    fun `적재 키워드는 앞뒤와 내부 공백을 접고 원본 대소문자를 보존한다`() {
        val userId = createActiveUser()
        val token = mintAccessToken(userId)

        // 전각 공백(U+3000)과 NEL(U+0085)을 내부에 둔 원본. 저장은 trim + 공백 접기 + 대소문자 보존
        searchAsUser("  아이폰\u0085\u3000케이스  ", token)

        assertEquals(listOf("아이폰 케이스"), recentKeywords(userId))
    }

    @Test
    fun `커서 페이지 이동은 최근 검색어로 적재하지 않는다`() {
        val userId = createActiveUser()
        val token = mintAccessToken(userId)
        tx.execute {
            val mid = persistMidCategory()
            (1..3).forEach { persistProduct(mid, "커서적재 사료 $it") }
        }

        // 게스트 첫 페이지로 커서만 얻는다(게스트라 적재되지 않음)
        val cursor = JsonPath.read<String>(search("커서적재", size = 1), "$.data.nextCursor")

        // 로그인 사용자가 커서로 2페이지 요청 (새 검색이 아니라 미적재 확인)
        mockMvc
            .perform(
                get("/api/v1/search/products")
                    .param("keyword", "커서적재")
                    .param("size", "1")
                    .param("cursor", cursor)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $token"),
            ).andExpect(status().isOk)

        assertEquals(0L, countRecent(userId), "커서 페이지 이동은 적재하지 않는다")
    }

    // --- helpers ---

    private fun search(
        keyword: String,
        cursor: String? = null,
        size: Int? = null,
        sort: String? = null,
        minPrice: Long? = null,
        maxPrice: Long? = null,
        minRating: String? = null,
        category: Long? = null,
        freeShipping: Boolean? = null,
        excludeSoldOut: Boolean? = null,
    ): String {
        val request = get("/api/v1/search/products").param("keyword", keyword)
        cursor?.let { request.param("cursor", it) }
        size?.let { request.param("size", it.toString()) }
        sort?.let { request.param("sort", it) }
        minPrice?.let { request.param("minPrice", it.toString()) }
        maxPrice?.let { request.param("maxPrice", it.toString()) }
        minRating?.let { request.param("minRating", it) }
        category?.let { request.param("category", it.toString()) }
        freeShipping?.let { request.param("freeShipping", it.toString()) }
        excludeSoldOut?.let { request.param("excludeSoldOut", it.toString()) }
        return mockMvc
            .perform(request)
            .andExpect(status().isOk)
            .andReturn()
            .response
            .getContentAsString(Charsets.UTF_8)
    }

    private fun searchAsUser(
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

    private fun filterOf(keyword: String): ProductKeywordFilter =
        ProductKeywordFilter(
            keyword = keyword,
            minPrice = null,
            maxPrice = null,
            minRating = null,
            categoryId = null,
            freeShipping = false,
            excludeSoldOut = false,
        )

    private fun productNames(body: String): List<String> = JsonPath.read(body, "$.data.products[*].name")

    private fun recentKeywords(userId: Long): List<String> =
        tx.execute {
            em
                .createQuery(
                    "select r.keyword from RecentSearch r where r.userId = :uid order by r.searchedAt desc, r.id desc",
                    String::class.java,
                ).setParameter("uid", userId)
                .resultList
        }!!

    private fun countRecent(userId: Long): Long =
        tx.execute {
            em
                .createQuery("select count(r) from RecentSearch r where r.userId = :uid", java.lang.Long::class.java)
                .setParameter("uid", userId)
                .singleResult
                .toLong()
        }!!

    private fun countAllRecent(): Long =
        tx.execute {
            em.createQuery("select count(r) from RecentSearch r", java.lang.Long::class.java).singleResult.toLong()
        }!!

    private fun persistSellerWithFee(
        sellerId: Long,
        storeName: String,
        baseShippingFee: Long,
    ) {
        em.persist(Seller.open(userId = sellerId, storeName = storeName, baseShippingFee = baseShippingFee))
    }

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
