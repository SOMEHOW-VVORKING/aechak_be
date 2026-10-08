package com.aechak.seller.product

import com.aechak.api.support.IntegrationTestBase
import com.aechak.application.file.port.FileKey
import com.aechak.application.file.port.enums.UploadPurpose
import com.aechak.domain.product.category.Category
import com.aechak.domain.product.product.enums.SaleStatus
import com.aechak.domain.seller.seller.Seller
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.web.FilterChainProxy
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.time.Duration

/**
 * 품절 자동 전환 통합 테스트. 재고 변경 커밋 뒤 리스너가 판매 상태를 맞추는 사슬 전체를 고정함.
 * 깨지면 재고가 0인데 판매중으로 보이거나 재고를 채워도 품절로 굳음.
 * 리스너가 별도 스레드라 응답 시점에는 아직 안 바뀌어 있음. 그래서 전부 대기 단언임.
 */
class OptionStockTransitionIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var context: WebApplicationContext

    @Autowired
    private lateinit var securityFilterChain: FilterChainProxy

    private lateinit var mockMvc: MockMvc
    private var sellerUserId = 0L
    private lateinit var token: String
    private var leafCategoryId = 0L
    private lateinit var productId: String
    private var combinationId = 0L

    @BeforeEach
    fun setUp() {
        mockMvc =
            MockMvcBuilders
                .webAppContextSetup(context)
                .addFilters<DefaultMockMvcBuilder>(securityFilterChain)
                .build()
        sellerUserId = createActiveUser()
        token = mintAccessToken(sellerUserId)
        tx.execute { em.persist(Seller.open(sellerUserId, "애착상회$sellerUserId", 0L)) }
        tx.execute {
            val root = Category.create(null, Category.ROOT_DEPTH, "강아지", null, 1)
            em.persist(root)
            val mid = Category.create(root, Category.MID_DEPTH, "사료", null, 1)
            em.persist(mid)
            val leaf = Category.create(mid, Category.LEAF_DEPTH, "건사료", null, 1)
            em.persist(leaf)
            leafCategoryId = leaf.id
        }
        productId = registerProduct()
        combinationId = combinationIdOf(productId)
    }

    @Test
    fun `마지막 재고가 소진되면 상품이 품절이 된다`() {
        change("""{"stockDelta": -50}""")

        awaitSaleStatus(SaleStatus.OUT_OF_STOCK, "활성 재고 합이 0이면 품절로 내려가야 한다")
    }

    @Test
    fun `품절 상품에 재고가 생기면 판매중으로 돌아온다`() {
        change("""{"stockDelta": -50}""")
        awaitSaleStatus(SaleStatus.OUT_OF_STOCK, "전제: 품절 상태여야 한다")

        change("""{"stockDelta": 5}""")

        awaitSaleStatus(SaleStatus.ON_SALE, "재고가 생겼으니 판매중으로 돌아와야 한다")
    }

    @Test
    fun `판매중인 상품의 재고 증가는 상태를 바꾸지 않는다`() {
        // 전이를 한 번 태워 리스너가 살아 있는 걸 확인해야 아래 유지 단언이 무언가를 지킴
        change("""{"stockDelta": -50}""")
        awaitSaleStatus(SaleStatus.OUT_OF_STOCK, "전제: 리스너가 돌아 품절이 돼야 한다")
        change("""{"stockDelta": 50}""")
        awaitSaleStatus(SaleStatus.ON_SALE, "전제: 판매중으로 돌아와야 한다")

        change("""{"stockDelta": 5}""")

        assertSaleStatusStays(SaleStatus.ON_SALE, "판매중은 그대로여야 한다")
    }

    @Test
    fun `재고가 남은 조합을 비활성화하면 품절이 된다`() {
        change("""{"isActive": false}""")

        awaitSaleStatus(SaleStatus.OUT_OF_STOCK, "팔지 않기로 한 조합의 재고는 살 수 있는 수량이 아니다")
    }

    @Test
    fun `판매중지 상품은 재고가 바뀌어도 판매중지로 남는다`() {
        // 판매중지로 내리기 전에 전이를 한 번 태워 리스너가 살아 있는 걸 확인함
        change("""{"stockDelta": -50}""")
        awaitSaleStatus(SaleStatus.OUT_OF_STOCK, "전제: 리스너가 돌아 품절이 돼야 한다")

        mockMvc
            .perform(
                patch("/api/v1/sellers/me/products/$productId/status")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"saleStatus": "SUSPENDED"}"""),
            ).andExpect(status().isOk)

        change("""{"stockDelta": 5}""")

        assertSaleStatusStays(SaleStatus.SUSPENDED, "재고를 채워도 셀러가 직접 올려야 한다")
    }

    private fun awaitSaleStatus(
        expected: SaleStatus,
        message: String,
    ) = await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted { assertEquals(expected, saleStatusOf(productId), message) }

    /** 전이가 없어야 하는 경우. 리스너가 늦게 돌아 뒤집을 수 있으므로 일정 시간 유지되는지를 본다. */
    private fun assertSaleStatusStays(
        expected: SaleStatus,
        message: String,
    ) = await()
        .during(Duration.ofMillis(500))
        .atMost(Duration.ofSeconds(5))
        .untilAsserted { assertEquals(expected, saleStatusOf(productId), message) }

    private fun change(body: String) {
        mockMvc
            .perform(
                patch("/api/v1/sellers/me/products/$productId/options/$combinationId")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body),
            ).andExpect(status().isOk)
    }

    private fun saleStatusOf(publicId: String): SaleStatus =
        tx.execute {
            em
                .createQuery("select p.saleStatus from Product p where p.publicId = :publicId", SaleStatus::class.java)
                .setParameter("publicId", publicId)
                .singleResult
        }!!

    private fun combinationIdOf(publicId: String): Long =
        tx.execute {
            em
                .createQuery(
                    "select c.id from OptionCombination c where c.product.publicId = :publicId",
                    Long::class.javaObjectType,
                ).setParameter("publicId", publicId)
                .singleResult
        }!!

    private fun registerProduct(): String {
        val tmpKey = "${FileKey.tmpPrefixOf(sellerUserId, UploadPurpose.PRODUCT)}thumbnail.png"
        val body =
            mockMvc
                .perform(
                    post("/api/v1/sellers/me/products")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            """
                            {
                              "categoryId": $leafCategoryId,
                              "productName": "연어 건사료 2kg",
                              "regularPrice": 25000,
                              "thumbnailImageKey": "$tmpKey",
                              "optionCombinations": [{"optionValues": [], "additionalPrice": 0, "stockQuantity": 50}]
                            }
                            """.trimIndent(),
                        ),
                ).andExpect(status().isCreated)
                .andReturn()
                .response
                .getContentAsString(Charsets.UTF_8)
        return Regex("\"productId\":\"([^\"]+)\"").find(body)!!.groupValues[1]
    }
}
