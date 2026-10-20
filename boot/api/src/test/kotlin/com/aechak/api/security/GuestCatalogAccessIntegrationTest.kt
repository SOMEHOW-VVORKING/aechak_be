package com.aechak.api.security

import com.aechak.api.support.IntegrationTestBase
import com.aechak.domain.product.category.Category
import com.aechak.domain.product.product.Product
import com.aechak.domain.product.stats.ProductStats
import com.aechak.domain.seller.seller.Seller
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.web.FilterChainProxy
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext

/**
 * 토큰 없는 요청에 카탈로그를 어디까지 열어 주는지 고정한다.
 *
 * 상품 목록·검색·상세·옵션은 열고, 리뷰 조회와 찜하기는 닫는다. 리뷰 조회는 컨트롤러가 인증 주체를
 * non-null로 받아 로그인을 요구하고, 찜하기는 쓰기라서 게스트에게 열 수 없다. 상세 경로를 열면서
 * 세그먼트 한 칸 와일드카드를 쓴 이유가 바로 리뷰를 함께 열지 않기 위함이라, 그 경계를 테스트가 지킨다.
 *
 * 홈은 아직 비회원에게 열지 않는다. 그 정책은 홈 통합 테스트가 따로 고정한다.
 */
class GuestCatalogAccessIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var context: WebApplicationContext

    @Autowired
    private lateinit var securityFilterChain: FilterChainProxy

    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        mockMvc =
            MockMvcBuilders
                .webAppContextSetup(context)
                .addFilters<DefaultMockMvcBuilder>(securityFilterChain)
                .build()
    }

    @Test
    fun `게스트도 상품 목록을 조회한다`() {
        seedProduct()

        mockMvc
            .perform(get("/api/v1/products").param("page", "0").param("size", "10"))
            .andExpect(status().isOk)
    }

    @Test
    fun `게스트도 상품 상세를 조회하고 찜 여부는 false로 내려온다`() {
        val publicId = seedProduct()

        mockMvc
            .perform(get("/api/v1/products/$publicId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.productId").value(publicId))
            .andExpect(jsonPath("$.data.isLiked").value(false))
    }

    @Test
    fun `게스트도 상품 옵션을 조회한다`() {
        val publicId = seedProduct()

        mockMvc
            .perform(get("/api/v1/products/$publicId/options"))
            .andExpect(status().isOk)
    }

    @Test
    fun `게스트도 상품 검색을 쓴다`() {
        seedProduct()

        mockMvc
            .perform(get("/api/v1/search/products").param("keyword", "사료"))
            .andExpect(status().isOk)
    }

    @Test
    fun `없는 상품 상세도 인증 요구가 아니라 404로 내려온다`() {
        mockMvc
            .perform(get("/api/v1/products/01JZZZZZZZZZZZZZZZZZZZZZZZ"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `게스트는 상품 리뷰를 조회할 수 없다`() {
        val publicId = seedProduct()

        // 리뷰 조회 컨트롤러는 인증 주체를 non-null로 받는다. 상세를 열 때 세그먼트 한 칸
        // 와일드카드를 쓴 이유가 이 경로를 함께 열지 않기 위함이므로, 그 경계를 여기서 고정한다.
        mockMvc
            .perform(get("/api/v1/products/$publicId/reviews"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `게스트는 상품을 찜할 수 없다`() {
        val publicId = seedProduct()

        mockMvc
            .perform(post("/api/v1/products/$publicId/like"))
            .andExpect(status().isUnauthorized)
    }

    /** 노출 조건을 만족하는 상품 한 건을 심고 공개 식별자를 돌려준다. */
    private fun seedProduct(): String =
        tx.execute {
            val sellerId = 9001L
            if (em.find(Seller::class.java, sellerId) == null) {
                em.persist(Seller.open(userId = sellerId, storeName = "게스트검증상회", baseShippingFee = 3_000L))
            }
            val root = Category.create(null, 1, "강아지", null, 1)
            em.persist(root)
            val mid = Category.create(root, 2, "사료", null, 1)
            em.persist(mid)
            val product =
                Product.register(
                    category = mid,
                    sellerId = sellerId,
                    name = "강아지 사료 10kg",
                    description = null,
                    representativeImageKey = "products/guest-check.jpg",
                    regularPrice = 10_000L,
                    discountPrice = null,
                    discountStartAt = null,
                    discountEndAt = null,
                )
            em.persist(product)
            em.flush()
            em.persist(ProductStats.create(product.id))
            em.flush()
            product.publicId
        }!!

    @Test
    fun `목록과 상세가 같은 공개 식별자를 쓴다`() {
        val publicId = seedProduct()

        mockMvc
            .perform(get("/api/v1/products").param("page", "0").param("size", "10"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.products[0].productId").value(publicId))

        assertEquals(26, publicId.length, "공개 식별자는 ULID 26자다")
    }
}
