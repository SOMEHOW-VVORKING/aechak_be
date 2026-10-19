package com.aechak.seller.product

import com.aechak.api.support.IntegrationTestBase
import com.aechak.application.file.port.FileKey
import com.aechak.application.file.port.enums.UploadPurpose
import com.aechak.application.product.draft.service.ProductDraftService
import com.aechak.domain.product.category.Category
import com.aechak.domain.product.draft.ProductDraft
import com.aechak.domain.seller.seller.Seller
import com.aechak.domain.seller.seller.enums.SellerStatus
import com.aechak.seller.product.support.ProductDraftPayload
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.web.FilterChainProxy
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.time.LocalDateTime

/**
 * 셀러 상품 임시저장 통합 테스트(계약).
 * 임시저장은 등록과 달리 부분 입력을 그대로 받아야 하므로, 필수값 검증이 새어 들어오면 여기가 깨진다.
 * 상태코드·에러코드와 셀러별 격리, 개수 상한, 보관 기간도 함께 고정한다.
 */
class SellerProductDraftIntegrationTest : IntegrationTestBase() {
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
        leafCategoryId = openLeafCategory()
    }

    @Test
    fun `초안은 생성 조회 수정 삭제가 왕복한다`() {
        val draftId = createDraft(draftJson(productName = "연어 건사료 2kg", regularPrice = 25000L))

        mockMvc
            .perform(getDraft(token, draftId))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.draftId").value(draftId))
            .andExpect(jsonPath("$.data.productName").value("연어 건사료 2kg"))
            .andExpect(jsonPath("$.data.regularPrice").value(25000))
            .andExpect(jsonPath("$.data.savedAt").exists())

        mockMvc
            .perform(
                put("/api/v1/sellers/me/product-drafts/$draftId")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(draftJson(productName = "닭가슴살 간식", regularPrice = 9900L)),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.data.draftId").value(draftId))

        mockMvc
            .perform(getDraft(token, draftId))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.productName").value("닭가슴살 간식"))
            .andExpect(jsonPath("$.data.regularPrice").value(9900))

        mockMvc.perform(deleteDraft(token, draftId)).andExpect(status().isNoContent)
        mockMvc.perform(getDraft(token, draftId)).andExpect(status().isNotFound).andExpect(errorCode(40400))
    }

    @Test
    fun `수정 응답의 savedAt은 직후 조회와 같다`() {
        val draftId = createDraft(draftJson(productName = "처음"))

        val putResponse =
            mockMvc
                .perform(
                    put("/api/v1/sellers/me/product-drafts/$draftId")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(draftJson(productName = "나중")),
                ).andExpect(status().isOk)
                .andReturn()
                .response
                .getContentAsString(Charsets.UTF_8)
        val putSavedAt = Regex("\"savedAt\":\"([^\"]+)\"").find(putResponse)!!.groupValues[1]

        mockMvc.perform(getDraft(token, draftId)).andExpect(jsonPath("$.data.savedAt").value(putSavedAt))
    }

    @Test
    fun `상품명만 채운 초안도 저장되고 빈 필드는 빈 채로 돌아온다`() {
        val draftId = createDraft("""{"productName": "제목만 적어둔 상품"}""")

        mockMvc
            .perform(getDraft(token, draftId))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.productName").value("제목만 적어둔 상품"))
            .andExpect(jsonPath("$.data.categoryId").doesNotExist())
            .andExpect(jsonPath("$.data.regularPrice").doesNotExist())
            .andExpect(jsonPath("$.data.thumbnailImageKey").doesNotExist())
            .andExpect(jsonPath("$.data.additionalImageKeys").isEmpty)
            .andExpect(jsonPath("$.data.optionCombinations").isEmpty)
    }

    @Test
    fun `아무 필드도 없는 빈 본문도 초안이 된다`() {
        val draftId = createDraft("{}")

        mockMvc
            .perform(getDraft(token, draftId))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.draftId").value(draftId))
            .andExpect(jsonPath("$.data.productName").doesNotExist())
    }

    @Test
    fun `이미지 키와 옵션은 보낸 모양 그대로 돌아온다`() {
        val body =
            """
            {
              "productName": "옵션 있는 상품",
              "thumbnailImageKey": "tmp/$sellerUserId/products/rep.webp",
              "additionalImageKeys": ["tmp/$sellerUserId/products/a1.webp"],
              "detailImageKeys": ["tmp/$sellerUserId/products/d1.webp", "tmp/$sellerUserId/products/d2.webp"],
              "optionGroups": [{"name": "중량", "values": ["100g", "200g"]}],
              "optionCombinations": [
                {"optionValues": ["100g"], "additionalPrice": 0, "stockQuantity": 10},
                {"optionValues": ["200g"]}
              ]
            }
            """.trimIndent()
        val draftId = createDraft(body)

        mockMvc
            .perform(getDraft(token, draftId))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.thumbnailImageKey").value("tmp/$sellerUserId/products/rep.webp"))
            .andExpect(jsonPath("$.data.detailImageKeys.length()").value(2))
            .andExpect(jsonPath("$.data.optionGroups[0].name").value("중량"))
            .andExpect(jsonPath("$.data.optionGroups[0].values[1]").value("200g"))
            .andExpect(jsonPath("$.data.optionCombinations[0].stockQuantity").value(10))
            .andExpect(jsonPath("$.data.optionCombinations[1].optionValues[0]").value("200g"))
            .andExpect(jsonPath("$.data.optionCombinations[1].stockQuantity").doesNotExist())
    }

    @Test
    fun `목록은 최근 저장순으로 내려오고 개수를 함께 준다`() {
        val first = createDraft(draftJson(productName = "먼저 저장"))
        val second = createDraft(draftJson(productName = "나중 저장"))

        mockMvc
            .perform(getDrafts(token))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.totalCount").value(2))
            .andExpect(jsonPath("$.data.items[0].draftId").value(second))
            .andExpect(jsonPath("$.data.items[0].productName").value("나중 저장"))
            .andExpect(jsonPath("$.data.items[1].draftId").value(first))
    }

    @Test
    fun `남의 초안은 조회 수정 삭제 모두 404다`() {
        val draftId = createDraft(draftJson())
        val otherSeller = createActiveUser()
        openSeller(otherSeller)
        val otherToken = mintAccessToken(otherSeller)

        mockMvc.perform(getDraft(otherToken, draftId)).andExpect(status().isNotFound).andExpect(errorCode(40400))
        mockMvc
            .perform(
                put("/api/v1/sellers/me/product-drafts/$draftId")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $otherToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(draftJson(productName = "가로채기")),
            ).andExpect(status().isNotFound)
            .andExpect(errorCode(40400))
        mockMvc.perform(deleteDraft(otherToken, draftId)).andExpect(status().isNotFound).andExpect(errorCode(40400))

        mockMvc.perform(getDrafts(otherToken)).andExpect(status().isOk).andExpect(jsonPath("$.data.totalCount").value(0))
        mockMvc.perform(getDraft(token, draftId)).andExpect(status().isOk)
    }

    @Test
    fun `상한을 넘기면 422와 40401을 반환하고 하나를 지우면 다시 저장된다`() {
        repeat(ProductDraftService.MAX_PRODUCT_DRAFTS) { createDraft(draftJson(productName = "초안 $it")) }

        mockMvc
            .perform(createRequest(token, draftJson(productName = "한 개 초과")))
            .andExpect(status().isUnprocessableEntity)
            .andExpect(errorCode(40401))

        val oldest = draftIdsOf(token).last()
        mockMvc.perform(deleteDraft(token, oldest)).andExpect(status().isNoContent)
        createDraft(draftJson(productName = "자리 회복"))

        mockMvc.perform(getDrafts(token)).andExpect(jsonPath("$.data.totalCount").value(ProductDraftService.MAX_PRODUCT_DRAFTS))
    }

    @Test
    fun `상한에 걸려도 기존 초안 덮어쓰기는 통과한다`() {
        val draftIds = (1..ProductDraftService.MAX_PRODUCT_DRAFTS).map { createDraft(draftJson(productName = "초안 $it")) }

        mockMvc
            .perform(
                put("/api/v1/sellers/me/product-drafts/${draftIds.first()}")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(draftJson(productName = "덮어쓴 초안")),
            ).andExpect(status().isOk)

        mockMvc
            .perform(getDraft(token, draftIds.first()))
            .andExpect(jsonPath("$.data.productName").value("덮어쓴 초안"))
    }

    @Test
    fun `초안에 담아 둔 tmp 이미지 키로 정식 등록이 되고 초안은 삭제로 치운다`() {
        // 초안이 키를 승격하지 않고 보관하므로 등록 경로의 승격이 그대로 돌아야 한다
        val thumbnailKey = tmpKey("rep.webp")
        val detailKey = tmpKey("d1.webp")
        val draftId =
            createDraft(
                """
                {
                  "categoryId": $leafCategoryId,
                  "productName": "등록까지 간 상품",
                  "regularPrice": 25000,
                  "thumbnailImageKey": "$thumbnailKey",
                  "detailImageKeys": ["$detailKey"]
                }
                """.trimIndent(),
            )

        mockMvc
            .perform(getDraft(token, draftId))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.thumbnailImageKey").value(thumbnailKey))
            .andExpect(jsonPath("$.data.detailImageKeys[0]").value(detailKey))

        mockMvc
            .perform(
                post("/api/v1/sellers/me/products")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "categoryId": $leafCategoryId,
                          "productName": "등록까지 간 상품",
                          "regularPrice": 25000,
                          "thumbnailImageKey": "$thumbnailKey",
                          "detailImageKeys": ["$detailKey"],
                          "optionCombinations": [{"optionValues": [], "additionalPrice": 0, "stockQuantity": 50}]
                        }
                        """.trimIndent(),
                    ),
            ).andExpect(status().isCreated)

        // 초안이 내려준 tmp 키가 등록 경로의 승격을 실제로 탔는지는 저장된 키가 증명한다
        assertEquals(
            listOf("products/d1.webp", "products/rep.webp"),
            storedProductImageKeys(),
            "등록은 초안이 보관한 tmp 키를 정식 키로 승격해 저장해야 한다",
        )

        // 등록은 초안을 대신 지워 주지 않는다 — 치우는 주체가 삭제 EP임을 고정한다
        mockMvc.perform(getDrafts(token)).andExpect(jsonPath("$.data.totalCount").value(1))

        mockMvc.perform(deleteDraft(token, draftId)).andExpect(status().isNoContent)

        mockMvc
            .perform(getDrafts(token))
            .andExpect(jsonPath("$.data.totalCount").value(0))
            .andExpect(jsonPath("$.data.items").isEmpty)
        assertEquals(0L, draftRowCount(), "삭제는 행을 남기지 않아야 한다")
    }

    @Test
    fun `payload는 문자열이 아니라 JSON 객체로 저장된다`() {
        // 이중 인코딩되면 여기서 STRING이 나온다 — 프리필이 조용히 깨진다
        createDraft(draftJson(productName = "저장 모양 확인"))

        val jsonType =
            tx.execute {
                em.createNativeQuery("select json_type(payload) from product_drafts").singleResult as String
            }
        assertEquals("OBJECT", jsonType, "payload는 JSON 객체여야 한다")
    }

    @Test
    fun `빈 본문으로 덮어쓰면 채워 둔 필드가 전부 비워진다`() {
        val draftId =
            createDraft(
                """
                {
                  "categoryId": $leafCategoryId,
                  "productName": "채운 초안",
                  "description": "설명",
                  "regularPrice": 1000,
                  "discountPrice": 900,
                  "discountStartAt": "2026-08-11T10:00:00",
                  "discountEndAt": "2026-08-12T10:00:00",
                  "thumbnailImageKey": "${tmpKey("rep.webp")}",
                  "additionalImageKeys": ["${tmpKey("a1.webp")}"],
                  "detailImageKeys": ["${tmpKey("d1.webp")}"],
                  "optionGroups": [{"name": "중량", "values": ["100g"]}],
                  "optionCombinations": [{"optionValues": ["100g"], "additionalPrice": 0, "stockQuantity": 1}]
                }
                """.trimIndent(),
            )

        mockMvc
            .perform(
                put("/api/v1/sellers/me/product-drafts/$draftId")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"),
            ).andExpect(status().isOk)

        mockMvc
            .perform(getDraft(token, draftId))
            .andExpect(jsonPath("$.data.categoryId").doesNotExist())
            .andExpect(jsonPath("$.data.productName").doesNotExist())
            .andExpect(jsonPath("$.data.description").doesNotExist())
            .andExpect(jsonPath("$.data.regularPrice").doesNotExist())
            .andExpect(jsonPath("$.data.discountPrice").doesNotExist())
            .andExpect(jsonPath("$.data.discountStartAt").doesNotExist())
            .andExpect(jsonPath("$.data.discountEndAt").doesNotExist())
            .andExpect(jsonPath("$.data.thumbnailImageKey").doesNotExist())
            .andExpect(jsonPath("$.data.additionalImageKeys").isEmpty)
            .andExpect(jsonPath("$.data.detailImageKeys").isEmpty)
            .andExpect(jsonPath("$.data.optionGroups").isEmpty)
            .andExpect(jsonPath("$.data.optionCombinations").isEmpty)
    }

    @Test
    fun `조합이 지목하는 옵션값이 상한을 넘으면 90001을 반환한다`() {
        val values = (1..ProductDraftPayload.COMBINATION_VALUES_MAX + 1).joinToString(", ") { "\"v$it\"" }
        val body = """{"optionCombinations": [{"optionValues": [$values]}]}"""

        mockMvc.perform(createRequest(token, body)).andExpect(status().isBadRequest).andExpect(errorCode(90001))
    }

    @Test
    fun `보관 기간 경계에 아직 닿지 않은 초안은 살아 있다`() {
        val draftId = createDraft(draftJson(productName = "경계 초안"))
        ageDraft(draftId, LocalDateTime.now().minusDays(ProductDraftService.RETENTION_DAYS).plusMinutes(1))

        mockMvc.perform(getDraft(token, draftId)).andExpect(status().isOk)
        mockMvc.perform(getDrafts(token)).andExpect(jsonPath("$.data.totalCount").value(1))
    }

    @Test
    fun `보관 기간이 지난 초안은 덮어쓰기와 삭제도 404다`() {
        val expired = createDraft(draftJson(productName = "묵은 초안"))
        ageDraft(expired, LocalDateTime.now().minusDays(ProductDraftService.RETENTION_DAYS + 1))

        mockMvc
            .perform(
                put("/api/v1/sellers/me/product-drafts/$expired")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(draftJson(productName = "되살리기")),
            ).andExpect(status().isNotFound)
            .andExpect(errorCode(40400))
        mockMvc.perform(deleteDraft(token, expired)).andExpect(status().isNotFound).andExpect(errorCode(40400))
    }

    @Test
    fun `상세 설명이 상한을 넘으면 90001을 반환한다`() {
        val body = """{"description": "${"가".repeat(ProductDraft.DESCRIPTION_MAX + 1)}"}"""

        mockMvc.perform(createRequest(token, body)).andExpect(status().isBadRequest).andExpect(errorCode(90001))
    }

    @Test
    fun `옵션값 목록이 상한을 넘으면 90001을 반환한다`() {
        val values = (1..ProductDraftPayload.GROUP_VALUES_MAX + 1).joinToString(", ") { "\"v$it\"" }
        val body = """{"optionGroups": [{"name": "중량", "values": [$values]}]}"""

        mockMvc.perform(createRequest(token, body)).andExpect(status().isBadRequest).andExpect(errorCode(90001))
    }

    @Test
    fun `보관 기간이 지난 초안은 조회에서 사라지고 다음 저장 때 지워진다`() {
        val expired = createDraft(draftJson(productName = "묵은 초안"))
        ageDraft(expired, LocalDateTime.now().minusDays(ProductDraftService.RETENTION_DAYS + 1))

        mockMvc.perform(getDraft(token, expired)).andExpect(status().isNotFound).andExpect(errorCode(40400))
        mockMvc.perform(getDrafts(token)).andExpect(jsonPath("$.data.totalCount").value(0))
        assertEquals(1L, draftRowCount(), "조회만으로는 행이 지워지지 않아야 한다")

        createDraft(draftJson(productName = "새 초안"))

        assertEquals(1L, draftRowCount(), "저장 때 묵은 초안이 치워져야 한다")
        assertNull(productNameOf(expired), "지워진 초안은 더 이상 조회되지 않아야 한다")
    }

    @Test
    fun `셀러가 아닌 계정은 40004를 반환한다`() {
        val notSeller = mintAccessToken(createActiveUser())

        mockMvc.perform(createRequest(notSeller, draftJson())).andExpect(status().isForbidden).andExpect(errorCode(40004))
        mockMvc.perform(getDrafts(notSeller)).andExpect(status().isForbidden).andExpect(errorCode(40004))
    }

    @Test
    fun `정지된 셀러는 40004를 반환한다`() {
        tx.execute {
            em
                .createQuery("update Seller s set s.status = :st where s.userId = :id")
                .setParameter("st", SellerStatus.SUSPENDED)
                .setParameter("id", sellerUserId)
                .executeUpdate()
        }

        mockMvc.perform(createRequest(token, draftJson())).andExpect(status().isForbidden).andExpect(errorCode(40004))
    }

    @Test
    fun `없는 초안을 덮어쓰려 하면 404를 반환한다`() {
        mockMvc
            .perform(
                put("/api/v1/sellers/me/product-drafts/01K0000000000000000000000X")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(draftJson()),
            ).andExpect(status().isNotFound)
            .andExpect(errorCode(40400))
    }

    @Test
    fun `상품명이 컬럼 길이를 넘으면 90001을 반환한다`() {
        val body = """{"productName": "${"가".repeat(ProductDraft.NAME_MAX + 1)}"}"""

        mockMvc.perform(createRequest(token, body)).andExpect(status().isBadRequest).andExpect(errorCode(90001))
    }

    @Test
    fun `이미지 키가 컬럼 길이를 넘으면 90001을 반환한다`() {
        val body = """{"detailImageKeys": ["${"x".repeat(ProductDraft.IMAGE_KEY_MAX + 1)}"]}"""

        mockMvc.perform(createRequest(token, body)).andExpect(status().isBadRequest).andExpect(errorCode(90001))
    }

    @Test
    fun `인증이 없으면 401을 반환한다`() {
        mockMvc.perform(get("/api/v1/sellers/me/product-drafts")).andExpect(status().isUnauthorized)
    }

    private fun createDraft(body: String): String {
        val response =
            mockMvc
                .perform(createRequest(token, body))
                .andExpect(status().isCreated)
                .andReturn()
                .response
                .getContentAsString(Charsets.UTF_8)
        return Regex("\"draftId\":\"([^\"]+)\"").find(response)!!.groupValues[1]
    }

    private fun draftIdsOf(token: String): List<String> {
        val response =
            mockMvc
                .perform(getDrafts(token))
                .andExpect(status().isOk)
                .andReturn()
                .response
                .getContentAsString(Charsets.UTF_8)
        return Regex("\"draftId\":\"([^\"]+)\"").findAll(response).map { it.groupValues[1] }.toList()
    }

    private fun createRequest(
        token: String,
        body: String,
    ) = post("/api/v1/sellers/me/product-drafts")
        .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
        .contentType(MediaType.APPLICATION_JSON)
        .content(body)

    private fun getDrafts(token: String) = get("/api/v1/sellers/me/product-drafts").header(HttpHeaders.AUTHORIZATION, "Bearer $token")

    private fun getDraft(
        token: String,
        draftId: String,
    ) = get("/api/v1/sellers/me/product-drafts/$draftId").header(HttpHeaders.AUTHORIZATION, "Bearer $token")

    private fun deleteDraft(
        token: String,
        draftId: String,
    ) = delete("/api/v1/sellers/me/product-drafts/$draftId").header(HttpHeaders.AUTHORIZATION, "Bearer $token")

    private fun errorCode(code: Int) = jsonPath("$.errorCode").value(code)

    private fun draftJson(
        productName: String? = "임시 상품",
        regularPrice: Long? = null,
    ): String {
        val fields =
            listOfNotNull(
                productName?.let { "\"productName\": \"$it\"" },
                regularPrice?.let { "\"regularPrice\": $it" },
            )
        return fields.joinToString(", ", prefix = "{", postfix = "}")
    }

    private fun openSeller(userId: Long) {
        tx.execute { em.persist(Seller.open(userId, "애착상회", 0L)) }
    }

    private fun openLeafCategory(): Long =
        tx.execute {
            val root = Category.create(null, Category.ROOT_DEPTH, "강아지", null, 1)
            em.persist(root)
            val mid = Category.create(root, Category.MID_DEPTH, "사료", null, 1)
            em.persist(mid)
            val leaf = Category.create(mid, Category.LEAF_DEPTH, "건사료", null, 1)
            em.persist(leaf)
            leaf.id
        }!!

    /** 공통 업로드가 발급하는 tmp 키 형태. 초안은 이 상태로 보관하고 정식 등록이 승격한다. */
    private fun tmpKey(fileName: String): String = "${FileKey.tmpPrefixOf(sellerUserId, UploadPurpose.PRODUCT)}$fileName"

    private fun ageDraft(
        publicId: String,
        savedAt: LocalDateTime,
    ) {
        tx.execute {
            em
                .createQuery("update ProductDraft d set d.savedAt = :savedAt where d.publicId = :publicId")
                .setParameter("savedAt", savedAt)
                .setParameter("publicId", publicId)
                .executeUpdate()
        }
    }

    private fun storedProductImageKeys(): List<String> =
        tx.execute {
            em
                .createQuery(
                    "select i.storageKey from Product p join p._images i order by i.storageKey asc",
                    String::class.java,
                ).resultList
        }!!

    private fun draftRowCount(): Long =
        tx.execute {
            em.createQuery("select count(d) from ProductDraft d", Long::class.javaObjectType).singleResult
        }!!

    private fun productNameOf(publicId: String): String? =
        tx.execute {
            em
                .createQuery("select d.productName from ProductDraft d where d.publicId = :publicId", String::class.java)
                .setParameter("publicId", publicId)
                .resultList
                .firstOrNull()
        }
}
