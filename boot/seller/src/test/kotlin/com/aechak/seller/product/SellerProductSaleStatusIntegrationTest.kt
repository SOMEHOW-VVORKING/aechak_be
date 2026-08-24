package com.aechak.seller.product

import com.aechak.api.support.IntegrationTestBase
import com.aechak.application.file.port.FileKey
import com.aechak.application.file.port.enums.UploadPurpose
import com.aechak.application.product.product.usecase.ProductUseCase
import com.aechak.application.product.product.usecase.query.ProductSearchQuery
import com.aechak.common.error.BusinessException
import com.aechak.domain.order.group.DeliveryAddressSnapshot
import com.aechak.domain.order.group.OrderGroup
import com.aechak.domain.order.order.Order
import com.aechak.domain.order.order.OrderItem
import com.aechak.domain.product.category.Category
import com.aechak.domain.product.error.ProductErrorCode
import com.aechak.domain.product.product.enums.SaleStatus
import com.aechak.domain.seller.seller.Seller
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
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
import java.time.LocalDateTime

/**
 * 셀러 판매 상태 변경 통합 테스트. 판매중지가 구매자 노출을 어디까지 끊고 어디를 안 건드리는지를 고정한다.
 * 노출은 saleStatus 화이트리스트 한 줄이 목록, 검색, 상세, 옵션 네 경로를 동시에 거르므로 여기가 무너지면 넷이 함께 무너진다.
 */
class SellerProductSaleStatusIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var context: WebApplicationContext

    @Autowired
    private lateinit var securityFilterChain: FilterChainProxy

    @Autowired
    private lateinit var productUseCase: ProductUseCase

    private lateinit var mockMvc: MockMvc
    private var sellerUserId = 0L
    private lateinit var token: String
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
        productId = registerProduct()
    }

    @Test
    fun `판매중지하면 200과 바뀐 상태를 반환한다`() {
        mockMvc
            .perform(statusRequest(token, productId, "SUSPENDED"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.productId").value(productId))
            .andExpect(jsonPath("$.data.saleStatus").value("SUSPENDED"))
            .andExpect(jsonPath("$.data.updatedAt").exists())
    }

    @Test
    fun `응답의 updatedAt은 갱신 후 시각이다`() {
        val body =
            mockMvc
                .perform(statusRequest(token, productId, "SUSPENDED"))
                .andExpect(status().isOk)
                .andReturn()
                .response
                .getContentAsString(Charsets.UTF_8)

        val responseUpdatedAt = LocalDateTime.parse(Regex("\"updatedAt\":\"([^\"]+)\"").find(body)!!.groupValues[1])
        val createdAt =
            tx.execute { em.createQuery("select p.createdAt from Product p", LocalDateTime::class.java).singleResult }!!
        assertTrue(
            responseUpdatedAt.isAfter(createdAt),
            "갱신 전 값을 실으면 등록 시각 그대로 나간다. 응답: $responseUpdatedAt, 등록: $createdAt",
        )
    }

    @Test
    fun `판매중지하면 구매자 목록에서 사라진다`() {
        assertTrue(listedProductIds().contains(productId), "판매중이면 목록에 있어야 한다")

        mockMvc.perform(statusRequest(token, productId, "SUSPENDED")).andExpect(status().isOk)

        assertFalse(listedProductIds().contains(productId), "판매중지는 목록에서 빠져야 한다: ${listedProductIds()}")
    }

    @Test
    fun `판매중지하면 구매자 상세가 40000이 된다`() {
        mockMvc.perform(statusRequest(token, productId, "SUSPENDED")).andExpect(status().isOk)

        val thrown = assertThrows<BusinessException> { productUseCase.getProduct(productId, null) }
        assertEquals(
            ProductErrorCode.PRODUCT_NOT_FOUND,
            thrown.errorCode,
            "미노출과 미존재를 구분해 알리지 않는다. 실제: ${thrown.errorCode}",
        )
    }

    @Test
    fun `판매중지하면 구매자 옵션 조회도 40000이 된다`() {
        mockMvc.perform(statusRequest(token, productId, "SUSPENDED")).andExpect(status().isOk)

        val thrown = assertThrows<BusinessException> { productUseCase.getProductOptions(productId) }
        assertEquals(ProductErrorCode.PRODUCT_NOT_FOUND, thrown.errorCode, "옵션 조회도 같은 필터를 탄다")
    }

    @Test
    fun `재판매로 되돌리면 다시 노출된다`() {
        mockMvc.perform(statusRequest(token, productId, "SUSPENDED")).andExpect(status().isOk)
        mockMvc
            .perform(statusRequest(token, productId, "ON_SALE"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.saleStatus").value("ON_SALE"))

        assertTrue(listedProductIds().contains(productId), "재판매하면 목록에 돌아와야 한다")
    }

    @Test
    fun `재고가 없으면 판매중으로 올려도 품절로 내려간다`() {
        emptyAllStock()
        mockMvc.perform(statusRequest(token, productId, "SUSPENDED")).andExpect(status().isOk)

        mockMvc
            .perform(statusRequest(token, productId, "ON_SALE"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.saleStatus").value("OUT_OF_STOCK"))
    }

    @Test
    fun `재고가 없어도 판매중지는 그대로 반영된다`() {
        emptyAllStock()

        mockMvc
            .perform(statusRequest(token, productId, "SUSPENDED"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.saleStatus").value("SUSPENDED"))
    }

    @Test
    fun `판매중지해도 기존 주문이 가리키는 버전은 그대로 남는다`() {
        val versionId = tx.execute { firstVersionId() }!!
        placeOrder(versionId)

        mockMvc.perform(statusRequest(token, productId, "SUSPENDED")).andExpect(status().isOk)

        val purchased =
            tx.execute {
                em
                    .createQuery(
                        "select v.nameSnapshot, v.priceSnapshot, v.statusSnapshot from ProductVersion v where v.id = :id",
                        Array<Any>::class.java,
                    ).setParameter("id", versionId)
                    .singleResult
            }!!
        assertEquals("연어 건사료 2kg", purchased[0], "주문 내역의 상품명이 결제 당시 값이어야 한다")
        assertEquals(25_000L, purchased[1], "주문 내역의 정가가 결제 당시 값이어야 한다")
        assertEquals(
            SaleStatus.ON_SALE,
            purchased[2],
            "결제 당시 판매중이었다는 사실이 판매중지로 덮이면 안 된다",
        )
        assertEquals(versionId, tx.execute { orderedVersionId() }, "주문이 가리키는 버전 행 자체가 바뀌면 안 된다")
    }

    @Test
    fun `상태를 바꿔도 버전은 남지 않는다`() {
        mockMvc.perform(statusRequest(token, productId, "SUSPENDED")).andExpect(status().isOk)

        assertEquals(
            listOf(1),
            versionNos(),
            "재고에서 파생하는 전환도 안 남기므로 셀러 토글도 남기지 않아야 한다: ${versionNos()}",
        )
    }

    @Test
    fun `셀러가 못 지정하는 상태를 보내면 90001이다`() {
        assertRejected("OUT_OF_STOCK")
        assertRejected("ENDED")

        assertEquals(1L, countOf("ProductVersion"), "거절된 요청이 버전을 남기면 안 된다")
    }

    @Test
    fun `enum에 없는 값을 보내면 본문 파싱 단계에서 90001이다`() {
        assertRejected("FOO")
    }

    @Test
    fun `이미 그 상태를 다시 보내도 200이다`() {
        mockMvc
            .perform(statusRequest(token, productId, "ON_SALE"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.saleStatus").value("ON_SALE"))

        assertEquals(
            SaleStatus.ON_SALE,
            tx.execute { em.createQuery("select p.saleStatus from Product p", SaleStatus::class.java).singleResult },
            "원하는 결과와 같은 상태라 오류가 아니고 상태도 그대로여야 한다",
        )
    }

    @Test
    fun `남의 상품 상태 변경은 403이 아니라 404다`() {
        val otherSeller = createActiveUser()
        openSeller(otherSeller)

        mockMvc
            .perform(statusRequest(mintAccessToken(otherSeller), productId, "SUSPENDED"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.errorCode").value(40000))

        assertTrue(listedProductIds().contains(productId), "거절된 요청이 노출을 끊으면 안 된다")
    }

    @Test
    fun `없는 상품 상태 변경은 40000을 반환한다`() {
        mockMvc
            .perform(statusRequest(token, "01JZZZZZZZZZZZZZZZZZZZZZZZ", "SUSPENDED"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.errorCode").value(40000))
    }

    @Test
    fun `셀러가 아닌 계정은 40004를 반환한다`() {
        val notSeller = createActiveUser()
        mockMvc
            .perform(statusRequest(mintAccessToken(notSeller), productId, "SUSPENDED"))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.errorCode").value(40004))
    }

    @Test
    fun `미로그인 상태 변경은 401과 20004다`() {
        mockMvc
            .perform(
                patch("/api/v1/sellers/me/products/$productId/status")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"saleStatus": "SUSPENDED"}"""),
            ).andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.errorCode").value(20004))
    }

    private fun assertRejected(saleStatus: String) {
        mockMvc
            .perform(statusRequest(token, productId, saleStatus))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(90001))
    }

    /** 엔티티에 세터가 없어 bulk update로 비움. */
    private fun emptyAllStock() {
        tx.execute {
            em
                .createQuery("update OptionCombination c set c.stockQuantity = 0, c.updatedAt = CURRENT_TIMESTAMP")
                .executeUpdate()
        }
    }

    private fun countOf(entityName: String): Long =
        tx.execute {
            em.createQuery("select count(e) from $entityName e", Long::class.javaObjectType).singleResult
        }!!

    private fun listedProductIds(): List<String> = productUseCase.getProducts(ProductSearchQuery(), null).items.map { it.productId }

    /** 공통 업로드가 발급하는 tmp 키 형태. 승격이 소유자와 용도를 이 접두로 가른다. */
    private fun tmpKey(
        fileName: String,
        ownerId: Long = sellerUserId,
        purpose: UploadPurpose = UploadPurpose.PRODUCT,
    ): String = "${FileKey.tmpPrefixOf(ownerId, purpose)}$fileName"

    private fun openSeller(userId: Long) {
        tx.execute { em.persist(Seller.open(userId, "애착상회$userId", 0L)) }
    }

    private fun placeOrder(productVersionId: Long) {
        tx.execute {
            val group =
                OrderGroup.create(
                    buyerId = 9_999L,
                    deliveryAddressId = 0L,
                    deliveryAddress = deliveryAddressSnapshot(),
                    usedPoint = 0L,
                    totalProductAmount = 25_000L,
                    totalShippingFee = 0L,
                    idempotencyKey = "idem-$productVersionId",
                    expiresAt = LocalDateTime.now().plusMinutes(10),
                )
            em.persist(group)
            val item =
                OrderItem.of(
                    productId = internalProductId(),
                    optionCombinationId = combinationId(),
                    quantity = 1,
                    unitPriceSnapshot = 25_000L,
                    discountAllocatedAmount = 0L,
                    productVersionId = productVersionId,
                )
            em.persist(
                Order.create(
                    orderGroup = group,
                    sellerId = sellerUserId,
                    sellerNameSnapshot = "애착상회$sellerUserId",
                    allocatedCouponDiscount = 0L,
                    sellerShippingFee = 0L,
                    items = listOf(item),
                ),
            )
        }
    }

    private fun deliveryAddressSnapshot() =
        DeliveryAddressSnapshot(
            receiverNameEnc = "enc-receiver",
            contactNumberEnc = "enc-contact",
            zipCode = "12345",
            baseAddress = "서울시 애착구 멍냥로 1",
            detailAddress = null,
            deliveryMemo = null,
        )

    private fun internalProductId(): Long =
        em
            .createQuery("select p.id from Product p where p.publicId = :publicId", Long::class.javaObjectType)
            .setParameter("publicId", productId)
            .singleResult

    private fun combinationId(): Long =
        em.createQuery("select c.id from OptionCombination c", Long::class.javaObjectType).resultList.first()

    private fun firstVersionId(): Long =
        em
            .createQuery("select v.id from ProductVersion v order by v.versionNo asc", Long::class.javaObjectType)
            .resultList
            .first()

    private fun orderedVersionId(): Long =
        tx.execute {
            em.createQuery("select i.productVersionId from OrderItem i", Long::class.javaObjectType).singleResult
        }!!

    private fun versionNos(): List<Int> =
        tx.execute {
            em
                .createQuery("select v.versionNo from ProductVersion v order by v.versionNo asc", Int::class.javaObjectType)
                .resultList
        }!!

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

    private fun statusRequest(
        token: String,
        productId: String,
        saleStatus: String,
    ): MockHttpServletRequestBuilder =
        patch("/api/v1/sellers/me/products/$productId/status")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"saleStatus": "$saleStatus"}""")
}
