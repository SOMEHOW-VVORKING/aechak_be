package com.aechak.seller.product

import com.aechak.api.support.IntegrationTestBase
import com.aechak.application.file.port.FileKey
import com.aechak.application.file.port.enums.UploadPurpose
import com.aechak.domain.product.category.Category
import com.aechak.domain.seller.seller.Seller
import org.junit.jupiter.api.Assertions.assertEquals
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * 버전 이력 통합 테스트. 실 MySQL이라 UNIQUE(product_id, version_no) 번역도 실제 제약으로 검증한다.
 * 과거 주문이 가리키는 행을 고치거나 지우지 않는다는 이 기능의 핵심 불변식을 여기서 고정한다.
 */
class SellerProductVersionAppendIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var context: WebApplicationContext

    @Autowired
    private lateinit var securityFilterChain: FilterChainProxy

    private lateinit var mockMvc: MockMvc
    private lateinit var token: String
    private var sellerUserId = 0L
    private var leafCategoryId = 0L
    private lateinit var productId: String

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
        productId = registerProduct()
    }

    @Test
    fun `수정할 때마다 번호가 마지막 다음으로 붙는다`() {
        update(productName = "닭가슴살 큐브")
        update(regularPrice = 30_000L)
        update(description = "새 설명")

        assertEquals(listOf(1, 2, 3, 4), versionNos(), "등록 1에 수정 셋이 이어져야 한다: ${versionNos()}")
    }

    @Test
    fun `앞선 버전 행은 수정도 삭제도 되지 않는다`() {
        update(productName = "닭가슴살 큐브", regularPrice = 30_000L)

        val rows = snapshotRows()
        assertEquals(2, rows.size, "수정은 기존 행을 고치지 않고 새 행을 붙여야 한다")
        assertEquals(
            listOf(1, "연어 건사료 2kg", 25_000L),
            rows[0],
            "1번 행이 수정 전 값 그대로여야 한다. 여기가 흔들리면 과거 주문 표시가 바뀐다",
        )
        assertEquals(listOf(2, "닭가슴살 큐브", 30_000L), rows[1], "2번 행에 수정 반영 후 값이 굳어야 한다")
    }

    @Test
    fun `새 버전은 수정 전이 아니라 수정 후 값을 굳힌다`() {
        update(productName = "닭가슴살 큐브", regularPrice = 30_000L, thumbnailImageKey = "${tmpKey("new-thumb.png")}")

        val last =
            tx.execute {
                em
                    .createQuery(
                        "select v.nameSnapshot, v.priceSnapshot, v.thumbnailKeySnapshot from ProductVersion v " +
                            "order by v.versionNo desc",
                        Array<Any>::class.java,
                    ).resultList
                    .first()
            }!!
        assertEquals("닭가슴살 큐브", last[0], "새 버전의 상품명이 수정 후 값이어야 한다")
        assertEquals(30_000L, last[1], "새 버전의 정가가 수정 후 값이어야 한다")
        assertEquals("products/new-thumb.png", last[2], "새 버전의 대표 이미지가 승격된 정식 키여야 한다")
    }

    @Test
    fun `같은 번호를 동시에 잡으면 90003으로 번역된다`() {
        // 선행 INSERT가 2번을 넣고 커밋을 늦추는 동안, 아직 커밋 안 된 행을 못 본 수정 요청도 2번을 잡아 잠금에 걸렸다가 터진다.
        val productPk = tx.execute { productPk() }!!
        val inserted = CountDownLatch(1)
        val executor = Executors.newSingleThreadExecutor()
        val slowInsert =
            executor.submit {
                tx.execute {
                    em
                        .createNativeQuery(
                            "insert into product_versions " +
                                "(product_id, version_no, name_snapshot, price_snapshot, status_snapshot, " +
                                " thumbnail_key_snapshot, changed_by, created_at, updated_at) " +
                                "values (:productId, 2, '연어 건사료 2kg', 25000, 'ON_SALE', " +
                                " 'products/thumbnail.png', 'SELLER', now(), now())",
                        ).setParameter("productId", productPk)
                        .executeUpdate()
                    inserted.countDown()
                    Thread.sleep(SLOW_COMMIT_MILLIS) // 수정 요청이 잠금 대기에 들어갈 시간
                }
            }
        assertTrue(inserted.await(5, TimeUnit.SECONDS), "선행 INSERT가 끝나야 한다")

        mockMvc
            .perform(updateRequest(updateJson(productName = "닭가슴살 큐브")))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.errorCode").value(90003))

        slowInsert.get(5, TimeUnit.SECONDS)
        executor.shutdown()
    }

    private fun update(
        productName: String = "연어 건사료 2kg",
        regularPrice: Long = 25_000L,
        description: String? = null,
        thumbnailImageKey: String = "${tmpKey("thumbnail.png")}",
    ) {
        mockMvc
            .perform(updateRequest(updateJson(productName, regularPrice, description, thumbnailImageKey)))
            .andExpect(status().isOk)
    }

    /** 공통 업로드가 발급하는 tmp 키 형태. 승격이 소유자와 용도를 이 접두로 가른다. */
    private fun tmpKey(
        fileName: String,
        ownerId: Long = sellerUserId,
        purpose: UploadPurpose = UploadPurpose.PRODUCT,
    ): String = "${FileKey.tmpPrefixOf(ownerId, purpose)}$fileName"

    private fun versionNos(): List<Int> =
        tx.execute {
            em
                .createQuery("select v.versionNo from ProductVersion v order by v.versionNo asc", Int::class.javaObjectType)
                .resultList
        }!!

    private fun snapshotRows(): List<List<Any>> =
        tx.execute {
            em
                .createQuery(
                    "select v.versionNo, v.nameSnapshot, v.priceSnapshot from ProductVersion v order by v.versionNo asc",
                    Array<Any>::class.java,
                ).resultList
                .map { it.toList() }
        }!!

    private fun productPk(): Long =
        em
            .createQuery("select p.id from Product p where p.publicId = :publicId", Long::class.javaObjectType)
            .setParameter("publicId", productId)
            .singleResult

    private fun registerProduct(): String {
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

    private fun updateRequest(body: String): MockHttpServletRequestBuilder =
        put("/api/v1/sellers/me/products/$productId")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body)

    private fun updateJson(
        productName: String = "연어 건사료 2kg",
        regularPrice: Long = 25_000L,
        description: String? = null,
        thumbnailImageKey: String = "${tmpKey("thumbnail.png")}",
    ): String =
        """
        {
          "categoryId": $leafCategoryId,
          "productName": "$productName",
          ${description?.let { "\"description\": \"$it\"," } ?: ""}
          "regularPrice": $regularPrice,
          "thumbnailImageKey": "$thumbnailImageKey"
        }
        """.trimIndent()

    companion object {
        private const val SLOW_COMMIT_MILLIS = 500L
    }
}
