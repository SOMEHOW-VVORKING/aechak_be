package com.aechak.seller.product

import com.aechak.api.support.IntegrationTestBase
import com.aechak.application.file.port.FileKey
import com.aechak.application.file.port.enums.UploadPurpose
import com.aechak.domain.product.category.Category
import com.aechak.domain.product.product.enums.InspectionStatus
import com.aechak.domain.product.product.enums.SaleStatus
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
import java.time.LocalDateTime

/**
 * 셀러 상품 목록 통합 테스트. 본인 상품 한정, 필터·정렬·오프셋 페이지, 재고 합 계산, 셀러 상태 게이트를 고정한다.
 */
class SellerProductListIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var context: WebApplicationContext

    @Autowired
    private lateinit var securityFilterChain: FilterChainProxy

    private lateinit var mockMvc: MockMvc
    private var sellerUserId = 0L
    private lateinit var token: String
    private var rootCategoryId = 0L
    private var midCategoryId = 0L
    private var leafCategoryId = 0L
    private var otherLeafCategoryId = 0L

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
            val otherMid = Category.create(root, Category.MID_DEPTH, "간식", null, 2)
            em.persist(otherMid)
            val otherLeaf = Category.create(otherMid, Category.LEAF_DEPTH, "수제간식", null, 1)
            em.persist(otherLeaf)
            rootCategoryId = root.id
            midCategoryId = mid.id
            leafCategoryId = leaf.id
            otherLeafCategoryId = otherLeaf.id
        }
    }

    @Test
    fun `내 상품만 최신 등록순으로 반환한다`() {
        val (otherUserId, otherToken) = openOtherSeller()
        register(name = "남의 상품", ownerId = otherUserId, asToken = otherToken)
        val first = register(name = "먼저 등록")
        val second = register(name = "나중 등록")

        list()
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.products.length()").value(2))
            .andExpect(jsonPath("$.data.products[0].productId").value(second))
            .andExpect(jsonPath("$.data.products[1].productId").value(first))
            .andExpect(jsonPath("$.data.totalCount").value(2))
    }

    @Test
    fun `구매자 목록과 달리 미승인·판매중지 상품도 보인다`() {
        val pending = register(name = "심사중 상품")
        val suspended = register(name = "중지 상품")
        setInspectionStatus(pending, InspectionStatus.PENDING)
        setSaleStatus(suspended, SaleStatus.SUSPENDED)

        list()
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.products.length()").value(2))
    }

    @Test
    fun `오프셋 페이지네이션과 페이지 정보가 동작한다`() {
        register(name = "상품1")
        register(name = "상품2")
        val latest = register(name = "상품3")

        list("size" to "2")
            .andExpect(jsonPath("$.data.products.length()").value(2))
            .andExpect(jsonPath("$.data.products[0].productId").value(latest))
            .andExpect(jsonPath("$.data.totalCount").value(3))
            .andExpect(jsonPath("$.data.totalPages").value(2))
            .andExpect(jsonPath("$.data.page").value(0))
            .andExpect(jsonPath("$.data.hasNext").value(true))

        list("size" to "2", "page" to "1")
            .andExpect(jsonPath("$.data.products.length()").value(1))
            .andExpect(jsonPath("$.data.page").value(1))
            .andExpect(jsonPath("$.data.hasNext").value(false))
    }

    @Test
    fun `판매 상태 필터는 복수 지정이 가능하다`() {
        val suspended = register(name = "중지 상품")
        val ended = register(name = "종료 상품")
        register(name = "판매중 상품")
        setSaleStatus(suspended, SaleStatus.SUSPENDED)
        setSaleStatus(ended, SaleStatus.ENDED)

        list("saleStatus" to "SUSPENDED")
            .andExpect(jsonPath("$.data.products.length()").value(1))
            .andExpect(jsonPath("$.data.products[0].productId").value(suspended))
            .andExpect(jsonPath("$.data.products[0].saleStatus").value("SUSPENDED"))

        mockMvc
            .perform(
                get(LIST_URL)
                    .param("saleStatus", "SUSPENDED", "ENDED")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $token"),
            ).andExpect(jsonPath("$.data.products.length()").value(2))
    }

    @Test
    fun `검수 상태 필터가 적용된다`() {
        val pending = register(name = "심사중 상품")
        register(name = "승인 상품")
        setInspectionStatus(pending, InspectionStatus.PENDING)

        list("inspectionStatus" to "PENDING")
            .andExpect(jsonPath("$.data.products.length()").value(1))
            .andExpect(jsonPath("$.data.products[0].inspectionStatus").value("PENDING"))
    }

    @Test
    fun `상품명 키워드로 걸러진다`() {
        val matched = register(name = "연어 건사료 2kg")
        register(name = "닭가슴살 간식")

        list("keyword" to "건사료")
            .andExpect(jsonPath("$.data.products.length()").value(1))
            .andExpect(jsonPath("$.data.products[0].productId").value(matched))
    }

    @Test
    fun `카테고리 필터는 하위 분류를 포함한다`() {
        val inFeed = register(name = "사료 상품", categoryId = leafCategoryId)
        val inSnack = register(name = "간식 상품", categoryId = otherLeafCategoryId)

        list("category" to midCategoryId.toString())
            .andExpect(jsonPath("$.data.products.length()").value(1))
            .andExpect(jsonPath("$.data.products[0].productId").value(inFeed))

        list("category" to otherLeafCategoryId.toString())
            .andExpect(jsonPath("$.data.products.length()").value(1))
            .andExpect(jsonPath("$.data.products[0].productId").value(inSnack))

        list("category" to rootCategoryId.toString())
            .andExpect(jsonPath("$.data.products.length()").value(2))
    }

    @Test
    fun `없는 카테고리 필터는 40101을 반환한다`() {
        list("category" to "999999")
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.errorCode").value(40101))
    }

    @Test
    fun `등록일 범위 필터는 상한 날짜를 포함한다`() {
        val old = register(name = "옛 상품")
        val recent = register(name = "요즘 상품")
        setCreatedAt(old, LocalDateTime.of(2020, 1, 15, 10, 0))

        list("createdTo" to "2020-01-15")
            .andExpect(jsonPath("$.data.products.length()").value(1))
            .andExpect(jsonPath("$.data.products[0].productId").value(old))

        list("createdTo" to "2020-01-14")
            .andExpect(jsonPath("$.data.products.length()").value(0))

        list("createdFrom" to "2020-02-01")
            .andExpect(jsonPath("$.data.products.length()").value(1))
            .andExpect(jsonPath("$.data.products[0].productId").value(recent))
    }

    @Test
    fun `역전된 등록일 범위는 90001을 반환한다`() {
        list("createdFrom" to "2026-02-01", "createdTo" to "2026-01-01")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(90001))
    }

    @Test
    fun `총 재고는 활성 조합의 재고 합이다`() {
        val productId =
            register(
                name = "옵션 상품",
                optionGroups = """[{"name": "색상", "values": ["화이트", "블랙"]}]""",
                optionCombinations =
                    """[
                        {"optionValues": ["화이트"], "additionalPrice": 0, "stockQuantity": 10},
                        {"optionValues": ["블랙"], "additionalPrice": 0, "stockQuantity": 20}
                    ]""",
            )

        list().andExpect(jsonPath("$.data.products[0].totalStock").value(30))

        deactivateCombination(productId, "블랙")
        list().andExpect(jsonPath("$.data.products[0].totalStock").value(10))
    }

    @Test
    fun `재고 필터는 활성 조합 재고 기준이다`() {
        val depleted = register(name = "소진 상품", stock = 0)
        val stocked = register(name = "보유 상품", stock = 10)
        val deactivatedOnly = register(name = "비활성 재고 상품", stock = 10)
        deactivateCombination(deactivatedOnly, "기본")

        list("stock" to "sold_out")
            .andExpect(jsonPath("$.data.products.length()").value(2))
            .andExpect(jsonPath("$.data.products[0].productId").value(deactivatedOnly))
            .andExpect(jsonPath("$.data.products[1].productId").value(depleted))

        list("stock" to "in_stock")
            .andExpect(jsonPath("$.data.products.length()").value(1))
            .andExpect(jsonPath("$.data.products[0].productId").value(stocked))
    }

    @Test
    fun `가격순 정렬은 할인 반영가 기준이다`() {
        val yesterday =
            LocalDateTime
                .now()
                .minusDays(1)
                .withNano(0)
                .toString()
        val plain = register(name = "정가 상품", price = 30000L)
        val discounted = register(name = "할인 상품", price = 40000L, discountPrice = 10000L, discountStartAt = yesterday)

        list("sort" to "price_asc")
            .andExpect(jsonPath("$.data.products[0].productId").value(discounted))
            .andExpect(jsonPath("$.data.products[0].discountPrice").value(10000))
            .andExpect(jsonPath("$.data.products[1].productId").value(plain))

        list("sort" to "price_desc")
            .andExpect(jsonPath("$.data.products[0].productId").value(plain))
    }

    @Test
    fun `잘못된 필터 값은 90001을 반환한다`() {
        assertBadRequest("saleStatus" to "WRONG")
        assertBadRequest("inspectionStatus" to "wrong")
        assertBadRequest("sort" to "priceasc")
        assertBadRequest("stock" to "none")
        assertBadRequest("createdFrom" to "2026-13-01")
    }

    @Test
    fun `범위 밖 size와 음수 page는 90001을 반환한다`() {
        assertBadRequest("size" to "0")
        assertBadRequest("size" to "101")
        assertBadRequest("page" to "-1")
    }

    @Test
    fun `정지·탈퇴 셀러와 비셀러는 40006을 반환한다`() {
        register(name = "기존 상품")

        setSellerStatus(sellerUserId, SellerStatus.SUSPENDED)
        list().andExpect(status().isForbidden).andExpect(jsonPath("$.errorCode").value(40006))

        setSellerStatus(sellerUserId, SellerStatus.WITHDRAWN)
        list().andExpect(status().isForbidden).andExpect(jsonPath("$.errorCode").value(40006))

        val notSeller = createActiveUser()
        list(asToken = mintAccessToken(notSeller))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.errorCode").value(40006))
    }

    @Test
    fun `휴점·탈퇴신청 셀러는 조회할 수 있다`() {
        register(name = "기존 상품")

        setSellerStatus(sellerUserId, SellerStatus.PAUSED)
        list().andExpect(status().isOk).andExpect(jsonPath("$.data.products.length()").value(1))

        setSellerStatus(sellerUserId, SellerStatus.WITHDRAWAL_REQUESTED)
        list().andExpect(status().isOk).andExpect(jsonPath("$.data.products.length()").value(1))
    }

    private fun assertBadRequest(param: Pair<String, String>) {
        list(param)
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(90001))
    }

    private fun openSeller(userId: Long) {
        tx.execute { em.persist(Seller.open(userId, "애착상회", 0L)) }
    }

    private fun openOtherSeller(): Pair<Long, String> {
        val otherUserId = createActiveUser()
        tx.execute { em.persist(Seller.open(otherUserId, "옆집상회", 0L)) }
        return otherUserId to mintAccessToken(otherUserId)
    }

    /** 공통 업로드가 발급하는 tmp 키 형태 — 등록이 소유자·용도를 검증하며 정식 키로 승격한다. */
    private fun tmpKey(
        fileName: String,
        ownerId: Long,
    ): String = "${FileKey.tmpPrefixOf(ownerId, UploadPurpose.PRODUCT)}$fileName"

    private fun list(
        vararg params: Pair<String, String>,
        asToken: String = token,
    ): ResultActions =
        mockMvc.perform(
            params
                .fold(get(LIST_URL)) { request, (key, value) -> request.param(key, value) }
                .header(HttpHeaders.AUTHORIZATION, "Bearer $asToken"),
        )

    private fun register(
        name: String = "연어 건사료 2kg",
        price: Long = 25000L,
        categoryId: Long = leafCategoryId,
        stock: Int = 50,
        discountPrice: Long? = null,
        discountStartAt: String? = null,
        optionGroups: String? = null,
        optionCombinations: String? = null,
        ownerId: Long = sellerUserId,
        asToken: String = token,
    ): String {
        val combinations =
            optionCombinations ?: """[{"optionValues": [], "additionalPrice": 0, "stockQuantity": $stock}]"""
        val optional =
            listOfNotNull(
                discountPrice?.let { "\"discountPrice\": $it," },
                discountStartAt?.let { "\"discountStartAt\": \"$it\"," },
                optionGroups?.let { "\"optionGroups\": $it," },
            ).joinToString("\n")
        val body =
            """
            {
              "categoryId": $categoryId,
              "productName": "$name",
              "regularPrice": $price,
              $optional
              "thumbnailImageKey": "${tmpKey("thumbnail.png", ownerId)}",
              "additionalImageKeys": [],
              "detailImageKeys": [],
              "optionCombinations": $combinations
            }
            """.trimIndent()
        val response =
            mockMvc
                .perform(
                    post(LIST_URL)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer $asToken")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body),
                ).andExpect(status().isCreated)
                .andReturn()
                .response
                .getContentAsString(Charsets.UTF_8)
        return Regex("\"productId\":\"([^\"]+)\"").find(response)!!.groupValues[1]
    }

    private fun setSaleStatus(
        productId: String,
        saleStatus: SaleStatus,
    ) {
        tx.execute {
            em
                .createQuery("update Product p set p.saleStatus = :st where p.publicId = :pid")
                .setParameter("st", saleStatus)
                .setParameter("pid", productId)
                .executeUpdate()
        }
    }

    private fun setInspectionStatus(
        productId: String,
        inspectionStatus: InspectionStatus,
    ) {
        tx.execute {
            em
                .createQuery("update Product p set p.inspectionStatus = :st where p.publicId = :pid")
                .setParameter("st", inspectionStatus)
                .setParameter("pid", productId)
                .executeUpdate()
        }
    }

    private fun setCreatedAt(
        productId: String,
        createdAt: LocalDateTime,
    ) {
        tx.execute {
            em
                .createQuery("update Product p set p.createdAt = :at where p.publicId = :pid")
                .setParameter("at", createdAt)
                .setParameter("pid", productId)
                .executeUpdate()
        }
    }

    private fun setSellerStatus(
        userId: Long,
        status: SellerStatus,
    ) {
        tx.execute {
            em
                .createQuery("update Seller s set s.status = :st where s.userId = :id")
                .setParameter("st", status)
                .setParameter("id", userId)
                .executeUpdate()
        }
    }

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
        private const val LIST_URL = "/api/v1/sellers/me/products"
    }
}
