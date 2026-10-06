package com.aechak.seller.product

import com.aechak.api.support.IntegrationTestBase
import com.aechak.application.file.port.FileKey
import com.aechak.application.file.port.enums.UploadPurpose
import com.aechak.domain.product.category.Category
import com.aechak.domain.seller.seller.Seller
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.web.FilterChainProxy
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext

/**
 * 셀러 옵션 재고 변경 EP 통합 테스트. 증감 반영과 활성 셀러, 소유, 소속 검증의 응답 계약을 고정함.
 * 깨지면 남의 조합이 수정되거나, 잔량을 넘는 감소가 0에서 멈추지 않고 재고를 음수로 만들거나,
 * 반영량을 안 알려줘 화면이 요청대로 다 빠졌는지 판단하지 못함.
 */
class OptionStockChangeIntegrationTest : IntegrationTestBase() {
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
        openSeller(sellerUserId)
        tx.execute {
            val root = Category.create(null, Category.ROOT_DEPTH, "강아지", null, 1)
            em.persist(root)
            val mid = Category.create(root, Category.MID_DEPTH, "사료", null, 1)
            em.persist(mid)
            val leaf = Category.create(mid, Category.LEAF_DEPTH, "건사료", null, 1)
            em.persist(leaf)
            leafCategoryId = leaf.id
        }
        productId = registerProduct(token)
        combinationId = combinationIdOf(productId)
    }

    @Test
    fun `재고를 줄이면 200과 변경 후 재고를 반환한다`() {
        mockMvc
            .perform(changeRequest(token, productId, combinationId, """{"stockDelta": -3}"""))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.combinationId").value(combinationId))
            .andExpect(jsonPath("$.data.stockQuantity").value(47))
            .andExpect(jsonPath("$.data.isActive").value(true))
            .andExpect(jsonPath("$.data.updatedAt").exists())
    }

    @Test
    fun `활성만 내려도 반영되고 재고는 그대로다`() {
        mockMvc
            .perform(changeRequest(token, productId, combinationId, """{"isActive": false}"""))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.isActive").value(false))
            .andExpect(jsonPath("$.data.stockQuantity").value(50))
    }

    @Test
    fun `재고와 활성을 함께 보내면 둘 다 반영된다`() {
        mockMvc
            .perform(changeRequest(token, productId, combinationId, """{"stockDelta": 10, "isActive": false}"""))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.stockQuantity").value(60))
            .andExpect(jsonPath("$.data.isActive").value(false))
    }

    @Test
    fun `증감은 누적되어 서로를 덮지 않는다`() {
        mockMvc.perform(changeRequest(token, productId, combinationId, """{"stockDelta": 10}""")).andExpect(status().isOk)

        mockMvc
            .perform(changeRequest(token, productId, combinationId, """{"stockDelta": -3}"""))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.stockQuantity").value(57))
    }

    @Test
    fun `활성을 안 보낸 요청은 비활성을 되살리지 않는다`() {
        mockMvc.perform(changeRequest(token, productId, combinationId, """{"isActive": false}""")).andExpect(status().isOk)

        mockMvc
            .perform(changeRequest(token, productId, combinationId, """{"stockDelta": 3}"""))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.isActive").value(false))
            .andExpect(jsonPath("$.data.stockQuantity").value(53))
    }

    @Test
    fun `두 필드 다 없으면 90001이다`() {
        assertRejectedAsInvalid("""{}""")
    }

    @Test
    fun `stockDelta 0은 90001이다`() {
        assertRejectedAsInvalid("""{"stockDelta": 0}""")
    }

    @Test
    fun `stockDelta가 범위를 넘으면 90001이다`() {
        assertRejectedAsInvalid("""{"stockDelta": 1000001}""")
        assertRejectedAsInvalid("""{"stockDelta": -1000001}""")
    }

    @Test
    fun `숫자가 아닌 stockDelta는 본문 파싱 단계에서 90001이다`() {
        assertRejectedAsInvalid("""{"stockDelta": "많이"}""")
    }

    @Test
    fun `재고보다 많이 줄이면 0에서 멈추고 반영된 양을 알려준다`() {
        mockMvc
            .perform(changeRequest(token, productId, combinationId, """{"stockDelta": -51}"""))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.stockQuantity").value(0))
            .andExpect(jsonPath("$.data.appliedStockDelta").value(-50))

        assertEquals(0, stockOf(combinationId), "요청을 다 못 받아줘도 받아준 만큼은 남아야 한다")
    }

    @Test
    fun `요청대로 다 반영되면 반영량이 요청량과 같다`() {
        mockMvc
            .perform(changeRequest(token, productId, combinationId, """{"stockDelta": -3}"""))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.appliedStockDelta").value(-3))
    }

    @Test
    fun `재고가 이미 0이면 감소 요청의 반영량은 0이다`() {
        mockMvc.perform(changeRequest(token, productId, combinationId, """{"stockDelta": -50}""")).andExpect(status().isOk)

        mockMvc
            .perform(changeRequest(token, productId, combinationId, """{"stockDelta": -1}"""))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.stockQuantity").value(0))
            .andExpect(jsonPath("$.data.appliedStockDelta").value(0))
    }

    @Test
    fun `남의 상품의 조합 변경은 403이 아니라 404다`() {
        val otherSeller = createActiveUser()
        openSeller(otherSeller)

        mockMvc
            .perform(changeRequest(mintAccessToken(otherSeller), productId, combinationId, """{"stockDelta": 1}"""))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.errorCode").value(40000))

        assertEquals(50, stockOf(combinationId), "거절된 요청이 재고를 바꾸면 안 된다")
    }

    @Test
    fun `없는 조합은 40000이다`() {
        mockMvc
            .perform(changeRequest(token, productId, 999_999L, """{"stockDelta": 1}"""))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.errorCode").value(40000))
    }

    @Test
    fun `내 다른 상품의 조합 id를 섞어 보내도 40000이다`() {
        val secondProductId = registerProduct(token)

        mockMvc
            .perform(changeRequest(token, secondProductId, combinationId, """{"stockDelta": 1}"""))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.errorCode").value(40000))
    }

    @Test
    fun `셀러가 아닌 계정은 40004를 반환한다`() {
        val notSeller = createActiveUser()

        mockMvc
            .perform(changeRequest(mintAccessToken(notSeller), productId, combinationId, """{"stockDelta": 1}"""))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.errorCode").value(40004))
    }

    @Test
    fun `미로그인 변경은 401과 20004다`() {
        mockMvc
            .perform(
                patch("/api/v1/sellers/me/products/$productId/options/$combinationId")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"stockDelta": 1}"""),
            ).andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.errorCode").value(20004))
    }

    private fun assertRejectedAsInvalid(body: String) {
        mockMvc
            .perform(changeRequest(token, productId, combinationId, body))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(90001))
    }

    private fun changeRequest(
        token: String,
        productId: String,
        combinationId: Long,
        body: String,
    ): MockHttpServletRequestBuilder =
        patch("/api/v1/sellers/me/products/$productId/options/$combinationId")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body)

    private fun openSeller(userId: Long) {
        tx.execute { em.persist(Seller.open(userId, "애착상회$userId", 0L)) }
    }

    private fun tmpKey(fileName: String): String = "${FileKey.tmpPrefixOf(sellerUserId, UploadPurpose.PRODUCT)}$fileName"

    private fun registerProduct(token: String): String {
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
                              "thumbnailImageKey": "${tmpKey("thumbnail.png")}",
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

    private fun combinationIdOf(publicId: String): Long =
        tx.execute {
            em
                .createQuery(
                    "select c.id from OptionCombination c where c.product.publicId = :publicId",
                    Long::class.javaObjectType,
                ).setParameter("publicId", publicId)
                .singleResult
        }!!

    private fun stockOf(combinationId: Long): Int =
        tx.execute {
            em
                .createQuery("select c.stockQuantity from OptionCombination c where c.id = :id", Int::class.javaObjectType)
                .setParameter("id", combinationId)
                .singleResult
        }!!
}
