package com.aechak.seller.product

import com.aechak.api.support.IntegrationTestBase
import com.aechak.application.file.port.FileKey
import com.aechak.application.file.port.enums.UploadPurpose
import com.aechak.domain.product.category.Category
import com.aechak.domain.seller.seller.Seller
import com.aechak.domain.seller.seller.enums.SellerStatus
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

/**
 * 셀러 상품 옵션 재고 조회 통합 테스트. 조합별 원값 노출, 비활성 조합 포함, 소유권(404/403 구분), 셀러 상태 게이트를 고정한다.
 */
class SellerProductOptionsIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var context: WebApplicationContext

    @Autowired
    private lateinit var securityFilterChain: FilterChainProxy

    private lateinit var mockMvc: MockMvc
    private var sellerUserId = 0L
    private lateinit var token: String
    private var leafCategoryId = 0L

    @BeforeEach
    fun setUp() {
        mockMvc =
            MockMvcBuilders
                .webAppContextSetup(context)
                .addFilters<DefaultMockMvcBuilder>(securityFilterChain)
                .build()
        sellerUserId = createActiveUser()
        token = mintAccessToken(sellerUserId)
        tx.execute { em.persist(Seller.open(sellerUserId, "애착상회", 0L)) }
        tx.execute {
            val root = Category.create(null, Category.ROOT_DEPTH, "강아지", null, 1)
            em.persist(root)
            val mid = Category.create(root, Category.MID_DEPTH, "사료", null, 1)
            em.persist(mid)
            val leaf = Category.create(mid, Category.LEAF_DEPTH, "건사료", null, 1)
            em.persist(leaf)
            leafCategoryId = leaf.id
        }
    }

    @Test
    fun `조합별 재고·추가금·활성 여부를 원값으로 반환한다`() {
        val productId =
            registerWithOptions(
                """[
                    {"optionValues": ["화이트"], "additionalPrice": 0, "stockQuantity": 10},
                    {"optionValues": ["블랙"], "additionalPrice": 1000, "stockQuantity": 0}
                ]""",
            )

        options(productId)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.optionCombinations.length()").value(2))
            .andExpect(jsonPath("$.data.optionCombinations[0].name").value("화이트"))
            .andExpect(jsonPath("$.data.optionCombinations[0].stockQuantity").value(10))
            .andExpect(jsonPath("$.data.optionCombinations[0].isActive").value(true))
            .andExpect(jsonPath("$.data.optionCombinations[1].name").value("블랙"))
            .andExpect(jsonPath("$.data.optionCombinations[1].additionalPrice").value(1000))
            .andExpect(jsonPath("$.data.optionCombinations[1].stockQuantity").value(0))
    }

    @Test
    fun `비활성 조합도 포함해 반환한다`() {
        val productId =
            registerWithOptions(
                """[
                    {"optionValues": ["화이트"], "additionalPrice": 0, "stockQuantity": 10},
                    {"optionValues": ["블랙"], "additionalPrice": 0, "stockQuantity": 20}
                ]""",
            )
        deactivateCombination(productId, "블랙")

        options(productId)
            .andExpect(jsonPath("$.data.optionCombinations.length()").value(2))
            .andExpect(jsonPath("$.data.optionCombinations[1].isActive").value(false))
    }

    @Test
    fun `옵션 없이 등록한 상품은 기본 조합 1행을 반환한다`() {
        val productId = register()

        options(productId)
            .andExpect(jsonPath("$.data.optionCombinations.length()").value(1))
            .andExpect(jsonPath("$.data.optionCombinations[0].name").value("기본"))
            .andExpect(jsonPath("$.data.optionCombinations[0].stockQuantity").value(50))
    }

    @Test
    fun `남의 상품이면 40007을 반환한다`() {
        val otherUserId = createActiveUser()
        tx.execute { em.persist(Seller.open(otherUserId, "옆집상회", 0L)) }
        val otherProductId = register(ownerId = otherUserId, asToken = mintAccessToken(otherUserId))

        options(otherProductId)
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.errorCode").value(40007))
    }

    @Test
    fun `없는 상품이면 40000을 반환한다`() {
        options("01ARZ3NDEKTSV4RRFFQ69G5FAV")
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.errorCode").value(40000))
    }

    @Test
    fun `정지 셀러는 40006을 반환한다`() {
        val productId = register()
        tx.execute {
            em
                .createQuery("update Seller s set s.status = :st where s.userId = :id")
                .setParameter("st", SellerStatus.SUSPENDED)
                .setParameter("id", sellerUserId)
                .executeUpdate()
        }

        options(productId)
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.errorCode").value(40006))
    }

    private fun options(
        productId: String,
        asToken: String = token,
    ): ResultActions =
        mockMvc.perform(
            get("$BASE_URL/$productId/options").header(HttpHeaders.AUTHORIZATION, "Bearer $asToken"),
        )

    private fun registerWithOptions(combinations: String): String =
        register(
            optionGroups = """[{"name": "색상", "values": ["화이트", "블랙"]}]""",
            optionCombinations = combinations,
        )

    private fun register(
        optionGroups: String? = null,
        optionCombinations: String? = null,
        ownerId: Long = sellerUserId,
        asToken: String = token,
    ): String {
        val combinations = optionCombinations ?: """[{"optionValues": [], "additionalPrice": 0, "stockQuantity": 50}]"""
        val optionalGroups = optionGroups?.let { "\"optionGroups\": $it," } ?: ""
        val body =
            """
            {
              "categoryId": $leafCategoryId,
              "productName": "연어 건사료 2kg",
              "regularPrice": 25000,
              $optionalGroups
              "thumbnailImageKey": "${tmpKey("thumbnail.png", ownerId)}",
              "additionalImageKeys": [],
              "detailImageKeys": [],
              "optionCombinations": $combinations
            }
            """.trimIndent()
        val response =
            mockMvc
                .perform(
                    post(BASE_URL)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer $asToken")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body),
                ).andExpect(status().isCreated)
                .andReturn()
                .response
                .getContentAsString(Charsets.UTF_8)
        return Regex("\"productId\":\"([^\"]+)\"").find(response)!!.groupValues[1]
    }

    /** 공통 업로드가 발급하는 tmp 키 형태 — 등록이 소유자·용도를 검증하며 정식 키로 승격한다. */
    private fun tmpKey(
        fileName: String,
        ownerId: Long,
    ): String = "${FileKey.tmpPrefixOf(ownerId, UploadPurpose.PRODUCT)}$fileName"

    private fun deactivateCombination(
        productId: String,
        combinationName: String,
    ) {
        tx.execute {
            em
                .createQuery(
                    "update OptionCombination c set c.isActive = false " +
                        "where c.name = :name and c.product.id in (select p.id from Product p where p.publicId = :pid)",
                ).setParameter("name", combinationName)
                .setParameter("pid", productId)
                .executeUpdate()
        }
    }

    companion object {
        private const val BASE_URL = "/api/v1/sellers/me/products"
    }
}
