package com.aechak.api.product.search

import com.aechak.api.support.IntegrationTestBase
import com.aechak.domain.product.category.Category
import com.aechak.domain.product.error.ProductErrorCode
import com.aechak.domain.product.product.Product
import com.aechak.domain.seller.seller.Seller
import com.jayway.jsonpath.JsonPath
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.web.FilterChainProxy
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import javax.sql.DataSource

/**
 * 키워드 검색 첫 페이지의 윈도 총개수가 따로 센 개수와 같은지, 쿼리 타임아웃이 검색 오류 코드로 바뀌는지 고정하는 통합 테스트
 */
class ProductKeywordSearchCountTimeoutIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var context: WebApplicationContext

    @Autowired
    private lateinit var securityFilterChain: FilterChainProxy

    @Autowired
    private lateinit var dataSource: DataSource

    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        mockMvc =
            MockMvcBuilders
                .webAppContextSetup(context)
                .addFilters<DefaultMockMvcBuilder>(securityFilterChain)
                .build()
        tx.execute {
            em.persist(Seller.open(userId = PAID_SELLER, storeName = "store-$PAID_SELLER", baseShippingFee = 3000L))
            em.persist(Seller.open(userId = FREE_SELLER, storeName = "store-$FREE_SELLER", baseShippingFee = 0L))
            val mid = Category.create(Category.create(null, 1, "강아지", null, 1).also { em.persist(it) }, 2, "사료간식", null, 1)
            em.persist(mid)
            persistProduct(mid, "강아지 사료 A", 10000L, PAID_SELLER)
            persistProduct(mid, "강아지 사료 B", 20000L, PAID_SELLER)
            persistProduct(mid, "고양이 사료", 30000L, FREE_SELLER)
            persistProduct(mid, "강아지 장난감", 15000L, PAID_SELLER)
            persistProduct(mid, "사료 그릇", 5000L, FREE_SELLER)
        }
    }

    @Test
    fun `첫 페이지 윈도 총개수는 필터, 빈 결과, 정확히 size인 결과에서 따로 센 개수와 같다`() {
        // (검색 조건, 기대 총개수). size는 일치 수보다 작거나 같게 둬 LIMIT과 무관하게 세는지 본다
        val cases =
            listOf(
                mapOf("keyword" to "사료", "size" to "2") to 4,
                mapOf("keyword" to "사료", "size" to "1", "maxPrice" to "20000") to 3,
                mapOf("keyword" to "사료", "size" to "1", "freeShipping" to "true") to 2,
                mapOf("keyword" to "강아지", "size" to "3") to 3,
                mapOf("keyword" to "없는키워드", "size" to "2") to 0,
            )
        cases.forEach { (params, expected) ->
            val firstPage = search(params)
            val separate = JsonPath.read<List<*>>(search(params + ("size" to "100")), "$.data.products").size

            assertEquals(expected, separate, "$params 따로 센 개수")
            assertEquals(separate, JsonPath.read<Int>(firstPage, "$.data.totalCount"), "$params 윈도 총개수")
        }
        assertFalse(JsonPath.read<Boolean>(search(mapOf("keyword" to "강아지", "size" to "3")), "$.data.hasNext"), "정확히 size면 다음 페이지가 없다")
    }

    @Test
    fun `검색 쿼리가 타임아웃되면 검색 지연 오류 코드로 503을 반환한다`() {
        // 다른 커넥션이 products 쓰기 락을 쥐면 검색 쿼리는 타임아웃까지 락을 기다린다
        dataSource.connection.use { lockHolder ->
            lockHolder.createStatement().use { it.execute("LOCK TABLES products WRITE") }
            try {
                mockMvc
                    .perform(get("/api/v1/search/products").param("keyword", "사료"))
                    .andExpect(status().isServiceUnavailable)
                    .andExpect(jsonPath("$.errorCode").value(ProductErrorCode.PRODUCT_SEARCH_TIMEOUT.code))
            } finally {
                lockHolder.createStatement().use { it.execute("UNLOCK TABLES") }
            }
        }
    }

    // --- helpers ---

    private fun search(params: Map<String, String>): String {
        val request = get("/api/v1/search/products")
        params.forEach { (name, value) -> request.param(name, value) }
        return mockMvc
            .perform(request)
            .andExpect(status().isOk)
            .andReturn()
            .response
            .getContentAsString(Charsets.UTF_8)
    }

    private fun persistProduct(
        category: Category,
        name: String,
        regular: Long,
        sellerId: Long,
    ) = em.persist(
        Product.register(
            category = category,
            sellerId = sellerId,
            name = name,
            description = null,
            representativeImageKey = "products/${name.hashCode()}.jpg",
            regularPrice = regular,
            discountPrice = null,
            discountStartAt = null,
            discountEndAt = null,
        ),
    )

    companion object {
        private const val PAID_SELLER = 77L
        private const val FREE_SELLER = 78L
    }
}
