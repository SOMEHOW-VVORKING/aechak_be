package com.aechak.seller.product

import com.aechak.api.support.IntegrationTestBase
import com.aechak.application.file.port.FileKey
import com.aechak.application.file.port.enums.UploadPurpose
import com.aechak.domain.product.category.Category
import com.aechak.domain.product.product.Product
import com.aechak.domain.seller.seller.Seller
import com.aechak.domain.seller.seller.enums.SellerStatus
import org.hamcrest.Matchers.contains
import org.hamcrest.Matchers.empty
import org.hamcrest.Matchers.nullValue
import org.junit.jupiter.api.Assertions.assertEquals
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import tools.jackson.databind.json.JsonMapper

/**
 * 셀러 상품 단건 조회 통합 테스트. 수정 폼 프리필에 필요한 필드가 전부 실리는지, 이미지 키가 저장 키 그대로
 * 현재 노출분만 나가는지, 받은 응답을 그대로 수정 요청에 실어도 유실 없이 통과하는지를 고정한다.
 */
class SellerProductDetailIntegrationTest : IntegrationTestBase() {
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
    }

    @Test
    fun `내 상품 단건은 수정 폼 프리필에 필요한 필드를 전부 반환한다`() {
        val productId = register()

        detail(productId)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.productId").value(productId))
            .andExpect(jsonPath("$.data.categoryId").value(leafCategoryId))
            .andExpect(jsonPath("$.data.productName").value("연어 건사료 2kg"))
            .andExpect(jsonPath("$.data.description").value("연어 90% 함유"))
            .andExpect(jsonPath("$.data.regularPrice").value(25_000))
            .andExpect(jsonPath("$.data.saleStatus").value("ON_SALE"))
            .andExpect(jsonPath("$.data.inspectionStatus").value("APPROVED"))
    }

    @Test
    fun `할인가와 할인 기간이 전부 실린다`() {
        val productId =
            register(
                discountPrice = 19_900L,
                discountStartAt = DISCOUNT_START,
                discountEndAt = DISCOUNT_END,
            )

        detail(productId)
            .andExpect(jsonPath("$.data.discountPrice").value(19_900))
            .andExpect(jsonPath("$.data.discountStartAt").value(DISCOUNT_START))
            .andExpect(jsonPath("$.data.discountEndAt").value(DISCOUNT_END))
    }

    @Test
    fun `할인을 설정하지 않은 상품은 할인 세 필드가 모두 null이다`() {
        val productId = register()

        detail(productId)
            .andExpect(jsonPath("$.data.discountPrice").value(nullValue()))
            .andExpect(jsonPath("$.data.discountStartAt").value(nullValue()))
            .andExpect(jsonPath("$.data.discountEndAt").value(nullValue()))
    }

    @Test
    fun `이미지는 종류별로 저장 키 그대로 등록 순서대로 실린다`() {
        val productId =
            register(
                additionalImageNames = listOf("a1.png", "a2.png"),
                detailImageNames = listOf("d1.png", "d2.png"),
            )

        detail(productId)
            .andExpect(jsonPath("$.data.thumbnailImageKey").value("products/thumbnail.png"))
            .andExpect(jsonPath("$.data.additionalImageKeys[0]").value("products/a1.png"))
            .andExpect(jsonPath("$.data.additionalImageKeys[1]").value("products/a2.png"))
            .andExpect(jsonPath("$.data.detailImageKeys[0]").value("products/d1.png"))
            .andExpect(jsonPath("$.data.detailImageKeys[1]").value("products/d2.png"))
    }

    @Test
    fun `이미지를 뺀 상품은 남은 키만 실린다`() {
        val productId = register(additionalImageNames = listOf("a1.png", "a2.png"))
        update(productId, updateJson(additionalImageKeys = listOf("products/a2.png")))

        detail(productId)
            .andExpect(jsonPath("$.data.additionalImageKeys.length()").value(1))
            .andExpect(jsonPath("$.data.additionalImageKeys[0]").value("products/a2.png"))
    }

    @Test
    fun `바뀐 이미지 순서가 응답 순서에 반영된다`() {
        val productId = register(additionalImageNames = listOf("a1.png", "a2.png"))
        update(productId, updateJson(additionalImageKeys = listOf("products/a2.png", "products/a1.png")))

        detail(productId)
            .andExpect(jsonPath("$.data.additionalImageKeys[0]").value("products/a2.png"))
            .andExpect(jsonPath("$.data.additionalImageKeys[1]").value("products/a1.png"))
    }

    @Test
    fun `수정을 두 번 거친 상품도 닫힌 과거 키를 섞지 않는다`() {
        val productId = register()
        update(
            productId,
            updateJson(
                thumbnailImageKey = tmpKey("thumb2.png"),
                additionalImageKeys = listOf(tmpKey("a2.png")),
                detailImageKeys = listOf("products/d1.png"),
            ),
        )
        update(
            productId,
            updateJson(
                thumbnailImageKey = "products/thumb2.png",
                additionalImageKeys = listOf("products/a2.png", tmpKey("a3.png")),
                detailImageKeys = emptyList(),
            ),
        )

        detail(productId)
            .andExpect(jsonPath("$.data.thumbnailImageKey").value("products/thumb2.png"))
            .andExpect(jsonPath("$.data.additionalImageKeys.length()").value(2))
            .andExpect(jsonPath("$.data.additionalImageKeys[0]").value("products/a2.png"))
            .andExpect(jsonPath("$.data.additionalImageKeys[1]").value("products/a3.png"))
            .andExpect(jsonPath("$.data.detailImageKeys").value(empty<String>()))
    }

    @Test
    fun `수정 이력이 있는 상품의 조회 응답을 그대로 되돌려 보내도 통과하고 값이 유실되지 않는다`() {
        val productId =
            register(
                discountPrice = 19_900L,
                discountStartAt = DISCOUNT_START,
                discountEndAt = DISCOUNT_END,
                additionalImageNames = listOf("a1.png", "a2.png"),
                detailImageNames = listOf("d1.png", "d2.png"),
            )
        update(
            productId,
            updateJson(
                thumbnailImageKey = tmpKey("thumb2.png"),
                additionalImageKeys = listOf("products/a2.png", "products/a1.png"),
                detailImageKeys = listOf("products/d2.png"),
                discountPrice = 19_900L,
                discountStartAt = DISCOUNT_START,
                discountEndAt = DISCOUNT_END,
            ),
        )
        update(
            productId,
            updateJson(
                thumbnailImageKey = "products/thumb2.png",
                additionalImageKeys = listOf("products/a2.png"),
                detailImageKeys = listOf("products/d2.png", tmpKey("d3.png")),
                discountPrice = 19_900L,
                discountStartAt = DISCOUNT_START,
                discountEndAt = DISCOUNT_END,
            ),
        )
        val before = detailData(productId)

        mockMvc
            .perform(
                put("$BASE_URL/$productId")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(before),
            ).andExpect(status().isOk)

        assertEquals(before, detailData(productId), "받은 응답을 그대로 되돌려 보낸 저장은 어떤 값도 바꾸면 안 된다")
        detail(productId)
            .andExpect(jsonPath("$.data.discountPrice").value(19_900))
            .andExpect(jsonPath("$.data.discountStartAt").value(DISCOUNT_START))
            .andExpect(jsonPath("$.data.discountEndAt").value(DISCOUNT_END))
            .andExpect(jsonPath("$.data.thumbnailImageKey").value("products/thumb2.png"))
            .andExpect(jsonPath("$.data.additionalImageKeys").value(contains("products/a2.png")))
            .andExpect(jsonPath("$.data.detailImageKeys").value(contains("products/d2.png", "products/d3.png")))
    }

    @Test
    fun `남의 상품은 403이 아니라 404다`() {
        val otherUserId = createActiveUser()
        openSeller(otherUserId)
        val otherProductId = register(ownerId = otherUserId, asToken = mintAccessToken(otherUserId))

        detail(otherProductId)
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.errorCode").value(40000))
    }

    @Test
    fun `없는 상품이면 40000을 반환한다`() {
        detail("01ARZ3NDEKTSV4RRFFQ69G5FAV")
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.errorCode").value(40000))
    }

    @Test
    fun `정지된 셀러는 40004를 반환한다`() {
        val productId = register()
        changeSellerStatus(SellerStatus.SUSPENDED)

        detail(productId)
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.errorCode").value(40004))
    }

    @Test
    fun `휴점 셀러와 탈퇴 신청 셀러도 40004를 반환한다`() {
        val productId = register()

        changeSellerStatus(SellerStatus.PAUSED)
        detail(productId)
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.errorCode").value(40004))

        changeSellerStatus(SellerStatus.WITHDRAWAL_REQUESTED)
        detail(productId)
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.errorCode").value(40004))
    }

    @Test
    fun `대표 이미지가 없는 상품은 thumbnailImageKey가 null로 나간다`() {
        val productId = persistProductWithoutRepresentativeImage()

        detail(productId)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.thumbnailImageKey").value(nullValue()))
            .andExpect(jsonPath("$.data.additionalImageKeys").value(empty<String>()))
    }

    @Test
    fun `셀러가 아닌 계정은 40004를 반환한다`() {
        val productId = register()
        val notSeller = createActiveUser()

        detail(productId, asToken = mintAccessToken(notSeller))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.errorCode").value(40004))
    }

    @Test
    fun `미로그인 조회는 401과 20004다`() {
        val productId = register()

        mockMvc
            .perform(get("$BASE_URL/$productId"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.errorCode").value(20004))
    }

    private fun detail(
        productId: String,
        asToken: String = token,
    ): ResultActions =
        mockMvc.perform(
            get("$BASE_URL/$productId").header(HttpHeaders.AUTHORIZATION, "Bearer $asToken"),
        )

    /** 조회 응답의 data 노드를 JSON 문자열로 돌려준다. 그대로 수정 요청 본문이 된다. */
    private fun detailData(productId: String): String {
        val body =
            detail(productId)
                .andExpect(status().isOk)
                .andReturn()
                .response
                .getContentAsString(Charsets.UTF_8)
        return JSON.readTree(body).get("data").toString()
    }

    private fun update(
        productId: String,
        body: String,
    ) {
        mockMvc
            .perform(
                put("$BASE_URL/$productId")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body),
            ).andExpect(status().isOk)
    }

    private fun changeSellerStatus(status: SellerStatus) {
        tx.execute {
            em
                .createQuery("update Seller s set s.status = :st where s.userId = :id")
                .setParameter("st", status)
                .setParameter("id", sellerUserId)
                .executeUpdate()
        }
    }

    /** 등록 API로는 만들 수 없는 상태 — 대표 이미지 키가 비어 이미지 행도 없는 상품을 직접 심는다. */
    private fun persistProductWithoutRepresentativeImage(): String =
        tx.execute {
            val product =
                Product.register(
                    category = em.find(Category::class.java, leafCategoryId),
                    sellerId = sellerUserId,
                    name = "대표 이미지 없는 상품",
                    description = null,
                    representativeImageKey = null,
                    regularPrice = 25_000L,
                    discountPrice = null,
                    discountStartAt = null,
                    discountEndAt = null,
                )
            em.persist(product)
            product.publicId
        }!!

    private fun openSeller(userId: Long) {
        tx.execute { em.persist(Seller.open(userId, "애착상회$userId", 0L)) }
    }

    private fun register(
        discountPrice: Long? = null,
        discountStartAt: String? = null,
        discountEndAt: String? = null,
        additionalImageNames: List<String> = listOf("a1.png"),
        detailImageNames: List<String> = listOf("d1.png"),
        ownerId: Long = sellerUserId,
        asToken: String = token,
    ): String {
        val discount =
            listOfNotNull(
                discountPrice?.let { "\"discountPrice\": $it," },
                discountStartAt?.let { "\"discountStartAt\": \"$it\"," },
                discountEndAt?.let { "\"discountEndAt\": \"$it\"," },
            ).joinToString("\n")
        val body =
            """
            {
              "categoryId": $leafCategoryId,
              "productName": "연어 건사료 2kg",
              "description": "연어 90% 함유",
              "regularPrice": 25000,
              $discount
              "thumbnailImageKey": "${tmpKey("thumbnail.png", ownerId)}",
              "additionalImageKeys": ${additionalImageNames.map { tmpKey(it, ownerId) }.toJsonArray()},
              "detailImageKeys": ${detailImageNames.map { tmpKey(it, ownerId) }.toJsonArray()},
              "optionCombinations": [{"optionValues": [], "additionalPrice": 0, "stockQuantity": 50}]
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

    private fun updateJson(
        thumbnailImageKey: String = "products/thumbnail.png",
        additionalImageKeys: List<String> = emptyList(),
        detailImageKeys: List<String> = emptyList(),
        discountPrice: Long? = null,
        discountStartAt: String? = null,
        discountEndAt: String? = null,
    ): String {
        val discount =
            listOfNotNull(
                discountPrice?.let { "\"discountPrice\": $it," },
                discountStartAt?.let { "\"discountStartAt\": \"$it\"," },
                discountEndAt?.let { "\"discountEndAt\": \"$it\"," },
            ).joinToString("\n")
        return """
            {
              "categoryId": $leafCategoryId,
              "productName": "연어 건사료 2kg",
              "description": "연어 90% 함유",
              "regularPrice": 25000,
              $discount
              "thumbnailImageKey": "$thumbnailImageKey",
              "additionalImageKeys": ${additionalImageKeys.toJsonArray()},
              "detailImageKeys": ${detailImageKeys.toJsonArray()}
            }
            """.trimIndent()
    }

    /** 공통 업로드가 발급하는 tmp 키 형태 — 등록·수정이 이 접두로 소유자와 용도를 가른다. */
    private fun tmpKey(
        fileName: String,
        ownerId: Long = sellerUserId,
    ): String = "${FileKey.tmpPrefixOf(ownerId, UploadPurpose.PRODUCT)}$fileName"

    private fun List<String>.toJsonArray(): String = joinToString(", ", prefix = "[", postfix = "]") { "\"$it\"" }

    companion object {
        private val JSON = JsonMapper.builder().build()

        private const val BASE_URL = "/api/v1/sellers/me/products"
        private const val DISCOUNT_START = "2026-10-01T00:00:00"
        private const val DISCOUNT_END = "2026-10-31T23:59:59"
    }
}
