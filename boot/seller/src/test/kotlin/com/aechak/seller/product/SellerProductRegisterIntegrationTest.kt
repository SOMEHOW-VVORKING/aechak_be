package com.aechak.seller.product

import com.aechak.api.support.IntegrationTestBase
import com.aechak.application.file.port.FileKey
import com.aechak.application.file.port.enums.UploadPurpose
import com.aechak.application.product.usecase.ProductUseCase
import com.aechak.application.product.usecase.query.ProductSearchQuery
import com.aechak.domain.product.category.Category
import com.aechak.domain.product.category.enums.CategoryStatus
import com.aechak.domain.product.product.enums.ProductImageType
import com.aechak.domain.product.version.enums.VersionChangeType
import com.aechak.domain.product.version.enums.VersionChangedBy
import com.aechak.domain.seller.seller.Seller
import com.aechak.domain.seller.seller.enums.SellerStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.web.FilterChainProxy
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext

/**
 * 셀러 상품 등록 통합 테스트. 상태코드와 에러코드, 등록 즉시 노출을 고정한다.
 * 한 요청이 상품, 이미지, 옵션, 승인본까지 남기므로 롤백이 함께 도는지도 여기서 본다.
 */
class SellerProductRegisterIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var context: WebApplicationContext

    @Autowired
    private lateinit var securityFilterChain: FilterChainProxy

    @Autowired
    private lateinit var productUseCase: ProductUseCase

    private lateinit var mockMvc: MockMvc
    private var sellerUserId = 0L
    private lateinit var token: String
    private var midCategoryId = 0L
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
            midCategoryId = mid.id
            leafCategoryId = leaf.id
        }
    }

    @Test
    fun `정상 등록은 201과 판매중 승인 상태를 반환한다`() {
        mockMvc
            .perform(registerRequest(token, productJson()))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.data.productId").isString)
            .andExpect(jsonPath("$.data.saleStatus").value("ON_SALE"))
            .andExpect(jsonPath("$.data.inspectionStatus").value("APPROVED"))
            .andExpect(jsonPath("$.data.versionNo").value(1))
            .andExpect(jsonPath("$.data.createdAt").exists())
    }

    @Test
    fun `등록한 상품은 검수 게이트 없이 구매자 목록에 바로 노출된다`() {
        val productId = registerAndReadProductId()

        val listed = productUseCase.getProducts(ProductSearchQuery()).items
        assertTrue(listed.any { it.productId == productId }, "등록 직후 목록에 보여야 한다: $listed")
    }

    @Test
    fun `이미지는 종류별로 sort_order를 0부터 매긴다`() {
        val body =
            productJson(
                additionalImageKeys = listOf(tmpKey("a1.png"), tmpKey("a2.png")),
                detailImageKeys = listOf(tmpKey("d1.png")),
            )
        mockMvc.perform(registerRequest(token, body)).andExpect(status().isCreated)

        assertEquals(listOf(0), sortOrdersOf(ProductImageType.REPRESENTATIVE), "대표는 1장이라 0 하나여야 한다")
        assertEquals(listOf(0, 1), sortOrdersOf(ProductImageType.PRODUCT), "추가 이미지는 0부터 다시 매겨야 한다")
        assertEquals(listOf(0), sortOrdersOf(ProductImageType.DETAIL), "상세 이미지도 0부터 다시 매겨야 한다")
    }

    @Test
    fun `상품명이 비어 있으면 90001을 반환한다`() {
        mockMvc
            .perform(registerRequest(token, productJson(productName = "")))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(90001))
    }

    @Test
    fun `필수 필드를 아예 빼면 본문 파싱 단계에서 90001을 반환한다`() {
        mockMvc
            .perform(registerRequest(token, """{"categoryId": $leafCategoryId, "regularPrice": 25000}"""))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(90001))
    }

    @Test
    fun `정가가 허용 범위를 벗어나면 40001을 반환한다`() {
        assertRejected(productJson(regularPrice = 99L), 40001)
        assertRejected(productJson(regularPrice = 100_000_001L), 40001)
    }

    @Test
    fun `등록은 승인본 1행을 같은 트랜잭션에서 남긴다`() {
        mockMvc
            .perform(registerRequest(token, productJson(productName = "연어 건사료 2kg", regularPrice = 25000L)))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.data.versionNo").value(1))

        val version =
            tx.execute {
                em
                    .createQuery(
                        "select v.versionNo, v.nameSnapshot, v.priceSnapshot, v.thumbnailKeySnapshot, v.changeType, v.changedBy " +
                            "from ProductVersion v",
                        Array<Any>::class.java,
                    ).singleResult
            }!!
        assertEquals(1, version[0], "등록 승인본의 번호는 1이어야 한다")
        assertEquals("연어 건사료 2kg", version[1], "등록 시점 상품명을 그대로 굳혀야 한다")
        assertEquals(25000L, version[2], "스냅샷 가격은 할인가가 아니라 정가여야 한다")
        assertEquals("products/thumbnail.png", version[3], "승격된 정식 키를 굳혀야 한다")
        assertEquals(VersionChangeType.INFO, version[4], "등록은 정보 변경으로 남아야 한다")
        assertEquals(VersionChangedBy.SELLER, version[5], "등록 주체는 셀러여야 한다")
    }

    @Test
    fun `등록은 통계 행을 0으로 초기화해 함께 남긴다`() {
        // 집계는 조건부 원자 UPDATE로만 갱신해서, 행이 없으면 첫 리뷰가 0행 갱신으로 조용히 사라진다
        mockMvc.perform(registerRequest(token, productJson())).andExpect(status().isCreated)

        val stats =
            tx.execute {
                em
                    .createQuery(
                        "select s.reviewCount, s.ratingSum, s.likeCount, s.averageRating from ProductStats s",
                        Array<Any>::class.java,
                    ).singleResult
            }!!
        assertEquals(0, stats[0], "리뷰 수는 0으로 시작해야 한다")
        assertEquals(0L, stats[1], "별점 합계는 0으로 시작해야 한다")
        assertEquals(0L, stats[2], "찜 수는 0으로 시작해야 한다")
        assertNull(stats[3], "리뷰가 없으면 평균 별점은 비어 있어야 한다")
    }

    @Test
    fun `등록이 거절되면 승인본도 남지 않는다`() {
        assertRejected(productJson(categoryId = midCategoryId), 40100)

        assertEquals(0L, countOf("ProductVersion"), "상품이 롤백되면 승인본도 함께 사라져야 한다")
        assertEquals(0L, countOf("ProductStats"), "상품이 롤백되면 통계 행도 함께 사라져야 한다")
        assertEquals(0L, countOf("Product"), "카테고리 거절이면 상품도 남으면 안 된다")
    }

    @Test
    fun `옵션이 없으면 기본 조합 1행이 생긴다`() {
        mockMvc.perform(registerRequest(token, productJson())).andExpect(status().isCreated)

        val combinations = combinationRows()
        assertEquals(1, combinations.size, "옵션이 없어도 담을 대상이 있어야 하므로 조합은 1행이다")
        assertEquals(listOf("기본" to ""), combinations.map { it.first to it.second }, "기본 조합의 서명은 빈 키다")
    }

    @Test
    fun `색상 2 사이즈 2는 조합 4행과 연결 8행이 된다`() {
        val body =
            productJson(
                optionGroups = """[
                    {"name": "색상", "values": ["화이트", "블랙"]},
                    {"name": "사이즈", "values": ["S", "M"]}
                ]""",
                optionCombinations =
                    combinationsJson(
                        listOf("화이트", "S") to 0,
                        listOf("화이트", "M") to 1000,
                        listOf("블랙", "S") to 0,
                        listOf("블랙", "M") to 1000,
                    ),
            )
        mockMvc.perform(registerRequest(token, body)).andExpect(status().isCreated)

        val combinations = combinationRows()
        assertEquals(4, combinations.size, "2x2면 조합이 4행이어야 한다: $combinations")
        assertTrue(combinations.any { it.first == "화이트 / M" }, "조합명은 옵션값을 이어 만든다: $combinations")
        assertTrue(combinations.all { it.second.matches(Regex("""\d+,\d+""")) }, "서명은 정렬된 옵션값 id다: $combinations")
        assertEquals(4, combinations.map { it.second }.distinct().size, "조합마다 서명이 달라야 한다")
        assertEquals(8, countOf("OptionCombinationValue"), "조합 4개가 값을 2개씩 물어 연결은 8행이어야 한다")
    }

    @Test
    fun `옵션값을 뒤집어 보내도 서명은 id 오름차순이다`() {
        // 순서를 그대로 이으면 같은 조합이 요청마다 다른 서명을 얻어 UNIQUE(product_id, value_signature)가 헛돈다
        val body =
            productJson(
                optionGroups = """[
                    {"name": "색상", "values": ["화이트"]},
                    {"name": "사이즈", "values": ["S"]}
                ]""",
                optionCombinations = combinationsJson(listOf("S", "화이트") to 0),
            )
        mockMvc.perform(registerRequest(token, body)).andExpect(status().isCreated)

        val signature = combinationRows().single().second
        val ids = signature.split(",").map { it.toLong() }
        assertEquals(ids.sorted(), ids, "서명이 오름차순이어야 한다: $signature")
    }

    @Test
    fun `옵션 추가금이 음수면 40201을 반환한다`() {
        assertRejected(productJson(optionCombinations = """[{"optionValues": [], "additionalPrice": -1, "stockQuantity": 1}]"""), 40201)
    }

    @Test
    fun `옵션 재고가 음수면 40200을 반환한다`() {
        assertRejected(productJson(optionCombinations = """[{"optionValues": [], "additionalPrice": 0, "stockQuantity": -1}]"""), 40200)
    }

    @Test
    fun `조합이 하나도 없으면 40005를 반환한다`() {
        assertRejected(productJson(optionCombinations = "[]"), 40005)
    }

    @Test
    fun `옵션값이 빈 조합을 두 건 보내면 40005를 반환한다`() {
        val body =
            productJson(
                optionCombinations =
                    """[
                        {"optionValues": [], "additionalPrice": 0, "stockQuantity": 10},
                        {"optionValues": [], "additionalPrice": 0, "stockQuantity": 20}
                    ]""",
            )
        assertRejected(body, 40005)
    }

    @Test
    fun `optionValues 키를 생략하면 본문 파싱 단계에서 90001을 반환한다`() {
        val body = productJson(optionCombinations = """[{"additionalPrice": 0, "stockQuantity": 10}]""")
        assertRejected(body, 90001)
    }

    @Test
    fun `같은 옵션값 조합을 두 번 보내면 40005를 반환한다`() {
        val body =
            productJson(
                optionGroups = """[{"name": "색상", "values": ["화이트", "블랙"]}]""",
                optionCombinations = combinationsJson(listOf("화이트") to 0, listOf("화이트") to 0),
            )
        assertRejected(body, 40005)
    }

    @Test
    fun `그룹에 없는 옵션값을 지목하면 40005를 반환한다`() {
        val body =
            productJson(
                optionGroups = """[{"name": "색상", "values": ["화이트"]}]""",
                optionCombinations = combinationsJson(listOf("레드") to 0),
            )
        assertRejected(body, 40005)
    }

    @Test
    fun `그룹이 있는데 조합이 옵션값을 안 지목하면 40005를 반환한다`() {
        val body = productJson(optionGroups = """[{"name": "색상", "values": ["화이트"]}]""")
        assertRejected(body, 40005)
    }

    @Test
    fun `그룹이 달라도 옵션값 이름이 겹치면 40005를 반환한다`() {
        val body =
            productJson(
                optionGroups = """[
                    {"name": "색상", "values": ["S"]},
                    {"name": "사이즈", "values": ["S"]}
                ]""",
                optionCombinations = combinationsJson(listOf("S", "S") to 0),
            )
        assertRejected(body, 40005)
    }

    @Test
    fun `이미지가 종류별 상한을 넘으면 40003을 반환한다`() {
        val body = productJson(detailImageKeys = (1..21).map { tmpKey("d$it.png") })
        assertRejected(body, 40003)
    }

    @Test
    fun `타인이 업로드한 이미지 키는 100002를 반환한다`() {
        val other = createActiveUser()
        val body = productJson(detailImageKeys = listOf(tmpKey("d1.png", ownerId = other)))
        mockMvc
            .perform(registerRequest(token, body))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.errorCode").value(100002))
    }

    @Test
    fun `발급 용도가 다른 이미지 키는 100003을 반환한다`() {
        val body = productJson(detailImageKeys = listOf(tmpKey("d1.png", purpose = UploadPurpose.USER_PROFILE)))
        assertRejected(body, 100003)
    }

    @Test
    fun `이미지 키가 컬럼 길이를 넘으면 90001을 반환한다`() {
        val body = productJson(detailImageKeys = listOf("x".repeat(1025)))
        mockMvc
            .perform(registerRequest(token, body))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(90001))
    }

    @Test
    fun `중분류를 지정하면 40100을 반환한다`() {
        mockMvc
            .perform(registerRequest(token, productJson(categoryId = midCategoryId)))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(40100))
    }

    @Test
    fun `비활성 카테고리는 40101을 반환한다`() {
        tx.execute {
            em
                .createQuery("update Category c set c.status = :st where c.id = :id")
                .setParameter("st", CategoryStatus.INACTIVE)
                .setParameter("id", leafCategoryId)
                .executeUpdate()
        }
        mockMvc
            .perform(registerRequest(token, productJson()))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.errorCode").value(40101))
    }

    @Test
    fun `셀러가 아닌 계정은 40004를 반환한다`() {
        val notSeller = createActiveUser()
        mockMvc
            .perform(registerRequest(mintAccessToken(notSeller), productJson()))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.errorCode").value(40004))
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
        mockMvc
            .perform(registerRequest(token, productJson()))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.errorCode").value(40004))
    }

    @Test
    fun `할인가에 시작일이 없으면 40002를 반환한다`() {
        assertRejected(productJson(discountPrice = 20000L), 40002)
    }

    @Test
    fun `할인 기간이 역전되면 40002를 반환한다`() {
        val body =
            productJson(
                discountPrice = 20000L,
                discountStartAt = "2026-08-11T10:00:00",
                discountEndAt = "2026-08-11T09:00:00",
            )
        assertRejected(body, 40002)
    }

    @Test
    fun `할인가 없이 기간만 지정하면 40002를 반환한다`() {
        assertRejected(productJson(discountStartAt = "2026-08-11T10:00:00"), 40002)
    }

    @Test
    fun `할인가가 정가를 넘으면 40001을 반환한다`() {
        val body = productJson(discountPrice = 30000L, discountStartAt = "2026-08-11T10:00:00")
        assertRejected(body, 40001)
    }

    private fun assertRejected(
        body: String,
        errorCode: Int,
    ) {
        mockMvc
            .perform(registerRequest(token, body))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(errorCode))
    }

    /** 공통 업로드가 발급하는 tmp 키 형태. 승격이 소유자와 용도를 이 접두로 가른다. */
    private fun tmpKey(
        fileName: String,
        ownerId: Long = sellerUserId,
        purpose: UploadPurpose = UploadPurpose.PRODUCT,
    ): String = "${FileKey.tmpPrefixOf(ownerId, purpose)}$fileName"

    private fun openSeller(userId: Long) {
        tx.execute { em.persist(Seller.open(userId, "애착상회", 0L)) }
    }

    /** 조합명과 서명 쌍. 서명은 옵션값 id라 값을 고정할 수 없어 형태만 본다. */
    private fun combinationRows(): List<Pair<String, String>> =
        tx.execute {
            em
                .createQuery("select c.name, c.valueSignature from OptionCombination c order by c.id asc", Array<Any>::class.java)
                .resultList
                .map { it[0] as String to it[1] as String }
        }!!

    private fun countOf(entityName: String): Long =
        tx.execute {
            em.createQuery("select count(e) from $entityName e", Long::class.javaObjectType).singleResult
        }!!

    private fun combinationsJson(vararg combinations: Pair<List<String>, Int>): String =
        combinations.joinToString(", ", prefix = "[", postfix = "]") { (values, additionalPrice) ->
            """{"optionValues": ${values.toJsonArray()}, "additionalPrice": $additionalPrice, "stockQuantity": 10}"""
        }

    private fun sortOrdersOf(imageType: ProductImageType): List<Int> =
        tx.execute {
            em
                .createQuery(
                    "select i.sortOrder from Product p join p._images i where i.imageType = :type order by i.sortOrder asc",
                    Int::class.javaObjectType,
                ).setParameter("type", imageType)
                .resultList
        }!!

    private fun registerAndReadProductId(): String {
        val body =
            mockMvc
                .perform(registerRequest(token, productJson()))
                .andExpect(status().isCreated)
                .andReturn()
                .response
                .getContentAsString(Charsets.UTF_8)
        return Regex("\"productId\":\"([^\"]+)\"").find(body)!!.groupValues[1]
    }

    private fun registerRequest(
        token: String,
        body: String,
    ): MockHttpServletRequestBuilder =
        post("/api/v1/sellers/me/products")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body)

    private fun productJson(
        categoryId: Long = leafCategoryId,
        productName: String = "연어 건사료 2kg",
        regularPrice: Long = 25000L,
        discountPrice: Long? = null,
        discountStartAt: String? = null,
        discountEndAt: String? = null,
        additionalImageKeys: List<String> = emptyList(),
        detailImageKeys: List<String> = emptyList(),
        optionGroups: String? = null,
        optionCombinations: String = """[{"optionValues": [], "additionalPrice": 0, "stockQuantity": 50}]""",
    ): String {
        val optional =
            listOfNotNull(
                discountPrice?.let { "\"discountPrice\": $it," },
                discountStartAt?.let { "\"discountStartAt\": \"$it\"," },
                discountEndAt?.let { "\"discountEndAt\": \"$it\"," },
                optionGroups?.let { "\"optionGroups\": $it," },
            ).joinToString("\n")
        return """
            {
              "categoryId": $categoryId,
              "productName": "$productName",
              "regularPrice": $regularPrice,
              $optional
              "thumbnailImageKey": "${tmpKey("thumbnail.png")}",
              "additionalImageKeys": ${additionalImageKeys.toJsonArray()},
              "detailImageKeys": ${detailImageKeys.toJsonArray()},
              "optionCombinations": $optionCombinations
            }
            """.trimIndent()
    }

    private fun List<String>.toJsonArray(): String = joinToString(", ", prefix = "[", postfix = "]") { "\"$it\"" }
}
