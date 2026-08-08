package com.aechak.api.order

import com.aechak.api.support.IntegrationTestBase
import com.aechak.domain.product.category.Category
import com.aechak.domain.product.option.OptionCombination
import com.aechak.domain.product.product.Product
import com.aechak.domain.product.product.enums.InspectionStatus
import com.aechak.domain.product.product.enums.SaleStatus
import com.aechak.domain.seller.seller.Seller
import com.aechak.domain.seller.seller.enums.SellerStatus
import com.jayway.jsonpath.JsonPath
import org.hibernate.SessionFactory
import org.hibernate.stat.Statistics
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.web.FilterChainProxy
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.time.LocalDateTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

/**
 * 담기·조회·수정·삭제 API 통합. HTTP 경계부터 실 MySQL까지 태워 검증 순서와 응답 계약을 고정함.
 * 깨지면 막아야 할 것을 통과시켰거나 응답이 프론트와 어긋난 것임.
 * Boot 4에는 @AutoConfigureMockMvc 슬라이스가 없어 WebApplicationContext와 실 보안 필터체인으로 MockMvc를 조립함.
 * 엔티티에 상태 세터가 없어 상태 픽스처는 persist 후 JPQL bulk update로 만듦.
 */
class CartIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var context: WebApplicationContext

    @Autowired
    private lateinit var securityFilterChain: FilterChainProxy

    private lateinit var mockMvc: MockMvc

    private val sellerUserId = 77L

    @BeforeEach
    fun setUp() {
        mockMvc =
            MockMvcBuilders
                .webAppContextSetup(context)
                .addFilters<DefaultMockMvcBuilder>(securityFilterChain)
                .build()
    }

    // ---------- 픽스처 헬퍼 ----------

    /** 옵션 조합 id 목록과 상품 publicId를 돌려줌. */
    private fun seedCatalog(
        stock: Int = 10,
        comboCount: Int = 1,
    ): Pair<List<Long>, String> =
        tx.execute {
            em.persist(Seller.open(sellerUserId, "행복한 펫샵", 3000L))
            val category = Category.create(null, 1, "강아지", null, 1)
            em.persist(category)
            val product = Product.register(category, sellerUserId, "강아지 사료 1kg", null, null, 19000L, null, null, null)
            em.persist(product)
            val comboIds =
                (1..comboCount).map { i ->
                    val combo = OptionCombination.create(product, "닭고기 / ${i}kg", 0L, stock, "sig-$i")
                    em.persist(combo)
                    combo.id
                }
            comboIds to product.publicId
        }!!

    private fun updateStock(
        comboId: Long,
        stock: Int,
    ) {
        tx.execute {
            em
                .createQuery("update OptionCombination oc set oc.stockQuantity = :q, oc.updatedAt = CURRENT_TIMESTAMP where oc.id = :id")
                .setParameter("q", stock)
                .setParameter("id", comboId)
                .executeUpdate()
        }
    }

    private fun deactivateOption(comboId: Long) {
        tx.execute {
            em
                .createQuery("update OptionCombination oc set oc.isActive = false where oc.id = :id")
                .setParameter("id", comboId)
                .executeUpdate()
        }
    }

    private fun updateSaleStatus(
        comboId: Long,
        status: SaleStatus,
    ) {
        tx.execute {
            em
                .createQuery(
                    "update Product p set p.saleStatus = :st, p.updatedAt = CURRENT_TIMESTAMP " +
                        "where p.id = (select oc.product.id from OptionCombination oc where oc.id = :id)",
                ).setParameter("st", status)
                .setParameter("id", comboId)
                .executeUpdate()
        }
    }

    private fun updateInspectionStatus(
        comboId: Long,
        status: InspectionStatus,
    ) {
        tx.execute {
            em
                .createQuery(
                    "update Product p set p.inspectionStatus = :st, p.updatedAt = CURRENT_TIMESTAMP " +
                        "where p.id = (select oc.product.id from OptionCombination oc where oc.id = :id)",
                ).setParameter("st", status)
                .setParameter("id", comboId)
                .executeUpdate()
        }
    }

    private fun updateSellerStatus(status: SellerStatus) {
        tx.execute {
            em
                .createQuery("update Seller s set s.status = :st where s.userId = :id")
                .setParameter("st", status)
                .setParameter("id", sellerUserId)
                .executeUpdate()
        }
    }

    private fun cartRowCount(buyerId: Long): Long =
        em
            .createQuery("select count(c) from Cart c where c.buyerId = :b", java.lang.Long::class.java)
            .setParameter("b", buyerId)
            .singleResult
            .toLong()

    private fun cartItemRowCount(): Long =
        em
            .createQuery("select count(ci) from CartItem ci", java.lang.Long::class.java)
            .singleResult
            .toLong()

    // ---------- HTTP 헬퍼 ----------

    private fun MockHttpServletRequestBuilder.bearer(token: String): MockHttpServletRequestBuilder =
        this.header(HttpHeaders.AUTHORIZATION, "Bearer $token")

    private fun cartItemJson(
        comboId: Long,
        quantity: Int,
    ): String = """{"optionCombinationId": $comboId, "quantity": $quantity}"""

    private fun postCartItem(
        token: String,
        body: String,
    ): MockHttpServletRequestBuilder =
        post("/api/v1/carts/items")
            .bearer(token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body)

    /** POST 후 201을 확인하고 응답 본문을 돌려줌. */
    private fun addCartItem(
        token: String,
        comboId: Long,
        quantity: Int,
    ): String =
        mockMvc
            .perform(postCartItem(token, cartItemJson(comboId, quantity)))
            .andExpect(status().isCreated)
            .andReturn()
            .response
            .getContentAsString(Charsets.UTF_8)

    private fun readLong(
        json: String,
        path: String,
    ): Long = (JsonPath.read(json, path) as Number).toLong()

    /** 기대값을 숫자로 직접 적음. enum에서 뽑아 쓰면 코드를 바꿔도 테스트가 따라와 아무것도 못 잡음. */
    private fun assertError(
        token: String,
        body: String,
        httpStatus: Int,
        errorCode: Int,
    ) {
        mockMvc
            .perform(postCartItem(token, body))
            .andExpect(status().`is`(httpStatus))
            .andExpect(jsonPath("$.errorCode").value(errorCode))
    }

    private fun quantityOf(comboId: Long): Int? =
        em
            .createQuery("select ci.quantity from CartItem ci where ci.optionCombinationId = :id", Integer::class.java)
            .setParameter("id", comboId)
            .resultList
            .firstOrNull()
            ?.toInt()

    // ---------- 담기 성공 ----------

    @Test
    fun `첫 담기는 201을 반환하고 장바구니를 lazy 생성한다`() {
        val buyerId = createActiveUser()
        val token = mintAccessToken(buyerId)
        val (comboIds, publicId) = seedCatalog()

        val body =
            mockMvc
                .perform(postCartItem(token, cartItemJson(comboIds[0], 2)))
                .andExpect(status().isCreated)
                .andReturn()
                .response
                .getContentAsString(Charsets.UTF_8)

        assertTrue(readLong(body, "$.data.cartItemId") > 0L, "생성된 cartItemId가 응답에 실려야 한다")
        assertEquals(publicId, JsonPath.read<String>(body, "$.data.productId"), "productId는 상품 publicId여야 한다")
        assertEquals(comboIds[0], readLong(body, "$.data.optionCombinationId"), "담은 옵션 조합 id가 응답에 실려야 한다")
        assertEquals(2, JsonPath.read<Int>(body, "$.data.quantity"), "담은 수량이 응답에 실려야 한다")
        assertEquals("ACTIVE", JsonPath.read<String>(body, "$.data.itemStatus"), "담기 직후 항목 상태는 ACTIVE여야 한다")
        assertEquals(2, JsonPath.read<Int>(body, "$.data.cartItemCount"), "cartItemCount는 담긴 수량의 합계여야 한다")
        assertEquals(1L, cartRowCount(buyerId), "첫 담기는 장바구니 행을 lazy 생성해 1개여야 한다")
    }

    @Test
    fun `동일 옵션 조합 재담기는 행을 늘리지 않고 수량을 누적한다`() {
        val buyerId = createActiveUser()
        val token = mintAccessToken(buyerId)
        val (comboIds, _) = seedCatalog()
        val firstId = readLong(addCartItem(token, comboIds[0], 2), "$.data.cartItemId")

        val body = addCartItem(token, comboIds[0], 3)

        assertEquals(firstId, readLong(body, "$.data.cartItemId"), "재담기는 라인을 삭제 후 재생성하지 않고 cartItemId를 유지해야 한다(INV-01)")
        assertEquals(5, JsonPath.read<Int>(body, "$.data.quantity"), "재담기는 기존 수량 2에 3을 누적해 5여야 한다")
        assertEquals(5, JsonPath.read<Int>(body, "$.data.cartItemCount"), "cartItemCount는 담긴 수량의 합계여야 한다")
        assertEquals(1L, cartItemRowCount(), "재담기는 라인 행을 늘리지 않아야 한다")
        assertEquals(1L, cartRowCount(buyerId), "재담기는 장바구니 행을 늘리지 않아야 한다")
    }

    @Test
    fun `cartItemCount는 품목 종류 수가 아니라 담긴 수량의 합계다`() {
        val buyerId = createActiveUser()
        val token = mintAccessToken(buyerId)
        val (comboIds, _) = seedCatalog(comboCount = 2)
        addCartItem(token, comboIds[0], 2)

        val body = addCartItem(token, comboIds[1], 3)

        assertEquals(3, JsonPath.read<Int>(body, "$.data.quantity"), "새 라인의 수량은 이번에 담은 3이어야 한다")
        assertEquals(5, JsonPath.read<Int>(body, "$.data.cartItemCount"), "cartItemCount는 2+3=5로 수량 합계여야 한다")
        assertEquals(2L, cartItemRowCount(), "다른 옵션 조합은 새 라인이어야 한다")
    }

    @Test
    fun `다중 기기 동시 첫 담기는 둘 다 성공하고 장바구니 행은 1개다`() {
        val buyerId = createActiveUser()
        val token = mintAccessToken(buyerId)
        val (comboIds, _) = seedCatalog()

        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        val statuses =
            try {
                (1..2)
                    .map {
                        pool.submit<Int> {
                            start.await()
                            mockMvc
                                .perform(postCartItem(token, cartItemJson(comboIds[0], 1)))
                                .andReturn()
                                .response
                                .status
                        }
                    }.also { start.countDown() }
                    .map { it.get() }
            } finally {
                pool.shutdown()
            }

        assertEquals(listOf(201, 201), statuses, "생성 경합이 나도 둘 다 성공해야 한다")
        assertEquals(1L, cartRowCount(buyerId), "동시 첫 담기여도 장바구니 행은 1개여야 한다")
        assertEquals(2, quantityOf(comboIds[0]), "두 요청이 각각 1씩 담았으므로 2여야 한다")
    }

    // ---------- 요청 계약(HTTP 경계) ----------

    @Test
    fun `원시 수량이 라인당 상한 99를 넘으면 90001이다`() {
        val buyerId = createActiveUser()
        val token = mintAccessToken(buyerId)
        val (comboIds, _) = seedCatalog()

        assertError(token, cartItemJson(comboIds[0], 100), 400, 90001)
    }

    @Test
    fun `필수 필드를 생략하면 90001이다`() {
        val buyerId = createActiveUser()
        val token = mintAccessToken(buyerId)

        // 기본값을 주면 생략이 그 값으로 채워져 파싱을 통과함
        assertError(token, """{"optionCombinationId": 1}""", 400, 90001)
    }

    @Test
    fun `미로그인 담기는 401과 20004다`() {
        mockMvc
            .perform(
                post("/api/v1/carts/items")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(cartItemJson(1L, 1)),
            ).andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.errorCode").value(20004))
    }

    // ---------- 검증 실패 ----------

    @Test
    fun `없는 옵션 조합은 50207이다`() {
        val buyerId = createActiveUser()
        val token = mintAccessToken(buyerId)
        seedCatalog()

        assertError(token, cartItemJson(999_999L, 1), 404, 50207)
    }

    @Test
    fun `검수 승인 상태가 아닌 상품은 50207이다`() {
        val buyerId = createActiveUser()
        val token = mintAccessToken(buyerId)
        val (comboIds, _) = seedCatalog()

        // 승인 외 상태를 열거하지 않고 전부 도는 이유는 상태가 늘 때 새 값이 자동으로 걸리게 하기 위함
        InspectionStatus.entries.filterNot { it == InspectionStatus.APPROVED }.forEach { status ->
            updateInspectionStatus(comboIds[0], status)

            assertError(token, cartItemJson(comboIds[0], 1), 404, 50207)
        }
        assertEquals(0L, cartItemRowCount(), "검수 미승인 담기는 라인을 남기지 않아야 한다")
    }

    @Test
    fun `옵션 조합이 비활성이면 50202다`() {
        val buyerId = createActiveUser()
        val token = mintAccessToken(buyerId)
        val (comboIds, _) = seedCatalog()
        deactivateOption(comboIds[0])

        assertError(token, cartItemJson(comboIds[0], 1), 409, 50202)
    }

    @Test
    fun `판매 중지 상품은 50202다`() {
        val buyerId = createActiveUser()
        val token = mintAccessToken(buyerId)
        val (comboIds, _) = seedCatalog()
        updateSaleStatus(comboIds[0], SaleStatus.SUSPENDED)

        assertError(token, cartItemJson(comboIds[0], 1), 409, 50202)
    }

    @Test
    fun `판매 종료 상품은 50202다`() {
        val buyerId = createActiveUser()
        val token = mintAccessToken(buyerId)
        val (comboIds, _) = seedCatalog()
        updateSaleStatus(comboIds[0], SaleStatus.ENDED)

        assertError(token, cartItemJson(comboIds[0], 1), 409, 50202)
    }

    @Test
    fun `셀러가 영업중이 아니면 상품 상태와 무관하게 50202다`() {
        val buyerId = createActiveUser()
        val token = mintAccessToken(buyerId)
        val (comboIds, _) = seedCatalog()
        updateSellerStatus(SellerStatus.PAUSED)

        assertError(token, cartItemJson(comboIds[0], 1), 409, 50202)
    }

    @Test
    fun `잔여 재고와 같은 수량까지는 담을 수 있다 - 경계`() {
        val buyerId = createActiveUser()
        val token = mintAccessToken(buyerId)
        val (comboIds, _) = seedCatalog(stock = 3)

        val body = addCartItem(token, comboIds[0], 3)

        assertEquals(3, JsonPath.read<Int>(body, "$.data.quantity"), "재고와 같은 수량은 담을 수 있어야 한다")
    }

    @Test
    fun `잔여 재고보다 많이 담으면 50201이다`() {
        val buyerId = createActiveUser()
        val token = mintAccessToken(buyerId)
        val (comboIds, _) = seedCatalog(stock = 1)

        assertError(token, cartItemJson(comboIds[0], 2), 409, 50201)
        assertEquals(0L, cartItemRowCount(), "실패한 담기는 라인을 남기지 않아야 한다")
    }

    @Test
    fun `재고 0으로 상품이 OUT_OF_STOCK이어도 품절 담기는 50202가 아니라 50201이다`() {
        val buyerId = createActiveUser()
        val token = mintAccessToken(buyerId)
        val (comboIds, _) = seedCatalog(stock = 0)
        updateSaleStatus(comboIds[0], SaleStatus.OUT_OF_STOCK)

        assertError(token, cartItemJson(comboIds[0], 1), 409, 50201)
    }

    @Test
    fun `누적 수량이 잔여 재고를 넘어도 50201이다`() {
        val buyerId = createActiveUser()
        val token = mintAccessToken(buyerId)
        val (comboIds, _) = seedCatalog(stock = 5)
        addCartItem(token, comboIds[0], 2)

        assertError(token, cartItemJson(comboIds[0], 4), 409, 50201)
        assertEquals(2, quantityOf(comboIds[0]), "실패한 누적은 저장된 수량을 바꾸지 않아야 한다")
    }

    @Test
    fun `라인당 99 초과와 재고 부족이 동시에 성립하면 요청 검증인 90001이 먼저다`() {
        val buyerId = createActiveUser()
        val token = mintAccessToken(buyerId)
        val (comboIds, _) = seedCatalog(stock = 98)
        addCartItem(token, comboIds[0], 98)
        updateStock(comboIds[0], 5)

        assertError(token, cartItemJson(comboIds[0], 2), 400, 90001)
        assertEquals(98, quantityOf(comboIds[0]), "실패한 누적은 저장된 수량을 바꾸지 않아야 한다")
    }

    @Test
    fun `판매 중지와 재고 부족이 동시에 성립하면 50201이 아니라 50202가 먼저다`() {
        val buyerId = createActiveUser()
        val token = mintAccessToken(buyerId)
        val (comboIds, _) = seedCatalog(stock = 1)
        updateSaleStatus(comboIds[0], SaleStatus.SUSPENDED)

        assertError(token, cartItemJson(comboIds[0], 2), 409, 50202)
    }

    @Test
    fun `품목 종류가 100개면 새 조합 담기는 50203이다`() {
        val buyerId = createActiveUser()
        val token = mintAccessToken(buyerId)
        val (comboIds, _) = seedCatalog(comboCount = 2)
        addCartItem(token, comboIds[0], 1)
        // 나머지 99종은 값참조(FK 없음)라 실제 옵션 조합 없이 행만 채워 상한 상태를 만듦.
        tx.execute {
            val cartId =
                em
                    .createQuery("select c.id from Cart c where c.buyerId = :b", java.lang.Long::class.java)
                    .setParameter("b", buyerId)
                    .singleResult
                    .toLong()
            val values =
                (1..99).joinToString(", ") { i ->
                    "($cartId, ${100_000L + i}, 1, now(), now())"
                }
            em
                .createNativeQuery(
                    "insert into cart_items (cart_id, option_combination_id, quantity, created_at, updated_at) values $values",
                ).executeUpdate()
        }

        assertError(token, cartItemJson(comboIds[1], 1), 422, 50203)
        assertEquals(100L, cartItemRowCount(), "상한에 걸린 담기는 라인을 늘리지 않아야 한다")
    }

    @Test
    fun `구매자별로 장바구니는 분리된다`() {
        val buyerA = createActiveUser()
        val buyerB = createActiveUser()
        val (comboIds, _) = seedCatalog()
        addCartItem(mintAccessToken(buyerA), comboIds[0], 2)

        val body = addCartItem(mintAccessToken(buyerB), comboIds[0], 3)

        assertEquals(3, JsonPath.read<Int>(body, "$.data.quantity"), "B의 담기는 A의 수량과 섞이지 않아야 한다")
        assertEquals(3, JsonPath.read<Int>(body, "$.data.cartItemCount"), "cartItemCount는 B 장바구니만의 합계여야 한다")
        assertEquals(1L, cartRowCount(buyerA), "A의 장바구니 행은 그대로 1개여야 한다")
        assertEquals(1L, cartRowCount(buyerB), "B의 장바구니 행도 1개여야 한다")
    }

    // ---------- 조회 픽스처 ----------

    private val thumbnailKey = "products/dog-food.jpg"

    /** 이미 있으면 그대로 둠. 상품 픽스처가 셀러 유무를 신경 쓰지 않게 하려는 것. */
    private fun seedSeller(
        userId: Long = sellerUserId,
        storeName: String = "행복한 펫샵",
        baseShippingFee: Long = 3000L,
        freeShippingThreshold: Long? = 50_000L,
    ) {
        tx.executeWithoutResult {
            if (em.find(Seller::class.java, userId) != null) return@executeWithoutResult
            em.persist(Seller.open(userId, storeName, baseShippingFee))
            em.flush()
            if (freeShippingThreshold == null) return@executeWithoutResult
            em
                .createQuery("update Seller s set s.freeShippingThreshold = :t where s.userId = :id")
                .setParameter("t", freeShippingThreshold)
                .setParameter("id", userId)
                .executeUpdate()
        }
    }

    /** 옵션 조합 id 목록과 상품 publicId를 돌려줌. */
    private fun seedProduct(
        sellerId: Long = sellerUserId,
        productName: String = "강아지 사료 1kg",
        imageKey: String? = thumbnailKey,
        regularPrice: Long = 19_000L,
        discountPrice: Long? = null,
        discountStartAt: LocalDateTime? = null,
        discountEndAt: LocalDateTime? = null,
        stock: Int = 10,
        additionalPrice: Long = 0L,
        optionNames: List<String> = listOf("닭고기 / 1kg"),
    ): Pair<List<Long>, String> {
        seedSeller(sellerId)
        return tx.execute {
            val category = Category.create(null, 1, "강아지", null, 1)
            em.persist(category)
            val product =
                Product.register(
                    category,
                    sellerId,
                    productName,
                    null,
                    imageKey,
                    regularPrice,
                    discountPrice,
                    discountStartAt,
                    discountEndAt,
                )
            em.persist(product)
            val comboIds =
                optionNames.mapIndexed { i, optionName ->
                    val combo = OptionCombination.create(product, optionName, additionalPrice, stock, "sig-$i")
                    em.persist(combo)
                    combo.id
                }
            comboIds to product.publicId
        }!!
    }

    /** 값참조(FK 없음)라 실재하지 않는 옵션 조합으로 라인을 만들 수 있음. 카탈로그 행이 사라진 상태를 흉내냄. */
    private fun insertOrphanCartItem(
        buyerId: Long,
        quantity: Int,
    ) {
        tx.executeWithoutResult {
            val cartId =
                em
                    .createQuery("select c.id from Cart c where c.buyerId = :b", java.lang.Long::class.java)
                    .setParameter("b", buyerId)
                    .singleResult
                    .toLong()
            em
                .createNativeQuery(
                    "insert into cart_items (cart_id, option_combination_id, quantity, created_at, updated_at) " +
                        "values ($cartId, 900001, $quantity, now(), now())",
                ).executeUpdate()
        }
    }

    // ---------- 조회 HTTP 헬퍼 ----------

    private fun getCart(token: String): String =
        mockMvc
            .perform(get("/api/v1/carts").bearer(token))
            .andExpect(status().isOk)
            .andReturn()
            .response
            .getContentAsString(Charsets.UTF_8)

    private fun firstItem(field: String) = "$.data.sellerGroups[0].items[0].$field"

    private fun itemStatusOf(token: String): String = JsonPath.read(getCart(token), firstItem("itemStatus"))

    /**
     * 이 헬퍼는 순서에 기대지 않고 sellerId로 찾음. 필터 결과는 배열이라 첫 원소를 꺼냄.
     * 필드를 경로로 바로 짚지 않고 그룹을 통째로 꺼내는 이유는, 값이 null인 필드가 필터 결과에서 빠질 수 있어서임.
     */
    private fun sellerGroup(
        body: String,
        sellerId: Long,
    ): Map<String, Any?> = JsonPath.read<List<Map<String, Any?>>>(body, "$.data.sellerGroups[?(@.sellerId == $sellerId)]").first()

    private fun keysOf(
        body: String,
        path: String,
    ): List<String> = JsonPath.read<Map<String, Any?>>(body, path).keys.sorted()

    // ---------- 조회 계약 ----------

    @Test
    fun `장바구니가 없으면 빈 응답을 주고 행을 만들지 않는다`() {
        val buyerId = createActiveUser()
        val token = mintAccessToken(buyerId)

        val body = getCart(token)

        assertEquals(0, JsonPath.read<Int>(body, "$.data.cartItemCount"), "빈 장바구니의 cartItemCount는 0이어야 한다")
        assertTrue(JsonPath.read<List<*>>(body, "$.data.sellerGroups").isEmpty(), "빈 장바구니의 sellerGroups는 비어야 한다")
        assertEquals(0L, cartRowCount(buyerId), "조회는 장바구니를 생성하지 않아야 한다. 생성은 담기만의 몫이다")
    }

    @Test
    fun `미로그인 조회는 401과 20004다`() {
        mockMvc
            .perform(get("/api/v1/carts"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.errorCode").value(20004))
    }

    @Test
    fun `남의 장바구니 항목은 내 조회에 섞이지 않는다`() {
        val buyerA = createActiveUser()
        val buyerB = createActiveUser()
        val (comboIds, _) = seedProduct()
        addCartItem(mintAccessToken(buyerB), comboIds[0], 3)
        addCartItem(mintAccessToken(buyerA), comboIds[0], 2)

        val body = getCart(mintAccessToken(buyerA))

        assertEquals(2, JsonPath.read<Int>(body, "$.data.cartItemCount"), "A의 조회에 B의 수량이 잡히면 buyerId 조건이 빠진 것이다")
        assertEquals(2, JsonPath.read<Int>(body, firstItem("quantity")), "A의 조회에는 A가 담은 수량만 실려야 한다")
    }

    // ---------- 셀러 그룹핑 ----------

    @Test
    fun `셀러별로 그룹을 나누고 배송정책을 함께 싣는다`() {
        val token = mintAccessToken(createActiveUser())
        seedSeller(sellerUserId, "행복한 펫샵", baseShippingFee = 3000L, freeShippingThreshold = 50_000L)
        seedSeller(88L, "튼튼 사료", baseShippingFee = 2500L, freeShippingThreshold = null)
        val (first, _) = seedProduct(sellerId = sellerUserId)
        val (second, _) = seedProduct(sellerId = 88L, productName = "고양이 간식")
        addCartItem(token, first[0], 1)
        addCartItem(token, second[0], 1)

        val body = getCart(token)
        val mine = sellerGroup(body, sellerUserId)
        val other = sellerGroup(body, 88L)

        assertEquals(2, JsonPath.read<List<*>>(body, "$.data.sellerGroups").size, "셀러가 둘이면 그룹도 둘이어야 한다")
        assertEquals("행복한 펫샵", mine["storeName"], "storeName은 sellers.store_name이어야 한다")
        assertEquals(3000, mine["baseShippingFee"], "기본 배송비를 셀러 그룹에 실어야 한다")
        assertEquals(50_000, mine["freeShippingThreshold"], "무료배송 임계를 셀러 그룹에 실어야 한다")
        assertEquals("튼튼 사료", other["storeName"], "둘째 셀러의 스토어명이 섞이지 않아야 한다")
        assertEquals(2500, other["baseShippingFee"], "셀러마다 자기 배송비가 실려야 한다")
        assertNull(other["freeShippingThreshold"], "무료배송 정책이 없는 셀러는 null이어야 한다")
        assertEquals(1, (other["items"] as List<*>).size, "그룹마다 자기 항목만 있어야 한다")
    }

    @Test
    fun `한 셀러의 여러 항목은 한 그룹으로 묶인다`() {
        val token = mintAccessToken(createActiveUser())
        val (first, _) = seedProduct()
        val (second, _) = seedProduct(productName = "고양이 간식")
        addCartItem(token, first[0], 1)
        addCartItem(token, second[0], 1)

        val body = getCart(token)

        assertEquals(1, JsonPath.read<List<*>>(body, "$.data.sellerGroups").size, "같은 셀러면 그룹이 하나여야 한다")
        assertEquals(2, JsonPath.read<List<*>>(body, "$.data.sellerGroups[0].items").size, "그 그룹 안에 항목 둘이 들어야 한다")
    }

    // ---------- 정렬 ----------

    @Test
    fun `항목은 최근 담은 순으로 내려온다`() {
        val token = mintAccessToken(createActiveUser())
        val (comboIds, _) = seedProduct(optionNames = listOf("먼저 담음", "나중 담음"))
        addCartItem(token, comboIds[0], 1)
        addCartItem(token, comboIds[1], 1)

        val body = getCart(token)

        assertEquals(
            listOf("나중 담음", "먼저 담음"),
            JsonPath.read<List<String>>(body, "$.data.sellerGroups[0].items[*].selectedOptions"),
            "최근 담은 항목이 위여야 한다",
        )
    }

    @Test
    fun `재담기로 수량만 늘어난 항목은 자리가 바뀌지 않는다`() {
        val token = mintAccessToken(createActiveUser())
        val (comboIds, _) = seedProduct(optionNames = listOf("먼저 담음", "나중 담음"))
        addCartItem(token, comboIds[0], 1)
        addCartItem(token, comboIds[1], 1)
        addCartItem(token, comboIds[0], 1) // 먼저 담은 라인의 수량만 누적

        val body = getCart(token)

        assertEquals(
            listOf("나중 담음", "먼저 담음"),
            JsonPath.read<List<String>>(body, "$.data.sellerGroups[0].items[*].selectedOptions"),
            "정렬 기준이 담은 시각이라 재담기로는 자리가 움직이지 않아야 한다. updatedAt으로 바뀌면 순서가 뒤집힌다",
        )
        assertEquals(
            2,
            JsonPath.read<Int>(body, "$.data.sellerGroups[0].items[1].quantity"),
            "자리는 그대로여도 먼저 담은 라인의 수량은 누적돼야 한다",
        )
    }

    @Test
    fun `셀러 그룹 순서는 가장 최근 담은 항목을 따라간다`() {
        val token = mintAccessToken(createActiveUser())
        seedSeller(sellerUserId, "행복한 펫샵")
        seedSeller(88L, "튼튼 사료")
        val (first, _) = seedProduct(sellerId = sellerUserId)
        val (second, _) = seedProduct(sellerId = 88L, productName = "고양이 간식")
        addCartItem(token, first[0], 1)
        addCartItem(token, second[0], 1)

        val body = getCart(token)

        assertEquals(
            listOf("튼튼 사료", "행복한 펫샵"),
            JsonPath.read<List<String>>(body, "$.data.sellerGroups[*].storeName"),
            "나중에 담은 셀러의 그룹이 위여야 한다",
        )
    }

    @Test
    fun `조회의 cartItemCount도 품목 종류 수가 아니라 수량 합계다`() {
        val token = mintAccessToken(createActiveUser())
        val (comboIds, _) = seedProduct(optionNames = listOf("닭고기 / 1kg", "연어 / 2kg"))
        addCartItem(token, comboIds[0], 2)
        addCartItem(token, comboIds[1], 3)

        val body = getCart(token)

        assertEquals(5, JsonPath.read<Int>(body, "$.data.cartItemCount"), "품목 종류는 2지만 수량 합계인 5여야 한다")
    }

    // ---------- itemStatus 파생 ----------

    @Test
    fun `정상 항목은 ACTIVE이고 isOrderable이 true다`() {
        val token = mintAccessToken(createActiveUser())
        val (comboIds, _) = seedProduct()
        addCartItem(token, comboIds[0], 1)

        val body = getCart(token)

        assertEquals("ACTIVE", JsonPath.read<String>(body, firstItem("itemStatus")), "정상 항목은 ACTIVE여야 한다")
        assertTrue(JsonPath.read<Boolean>(body, firstItem("isOrderable")), "ACTIVE면 isOrderable이 true여야 한다")
    }

    @Test
    fun `조합 재고가 0이면 OUT_OF_STOCK이고 isOrderable이 false다`() {
        val token = mintAccessToken(createActiveUser())
        val (comboIds, _) = seedProduct(stock = 5)
        addCartItem(token, comboIds[0], 1)
        updateStock(comboIds[0], 0)

        val body = getCart(token)

        assertEquals("OUT_OF_STOCK", JsonPath.read<String>(body, firstItem("itemStatus")), "조합 재고가 0이면 품절이어야 한다")
        assertEquals(false, JsonPath.read<Boolean>(body, firstItem("isOrderable")), "ACTIVE가 아니면 isOrderable이 false여야 한다")
        assertEquals(0, JsonPath.read<Int>(body, firstItem("remainingStock")), "품절이어도 잔여 재고를 그대로 내려야 한다")
    }

    @Test
    fun `옵션 조합이 비활성이면 OPTION_UNAVAILABLE이다`() {
        val token = mintAccessToken(createActiveUser())
        val (comboIds, _) = seedProduct()
        addCartItem(token, comboIds[0], 1)
        deactivateOption(comboIds[0])

        assertEquals("OPTION_UNAVAILABLE", itemStatusOf(token), "비활성 조합은 OPTION_UNAVAILABLE이어야 한다")
    }

    @Test
    fun `판매 중지 상품은 SALE_SUSPENDED다`() {
        val token = mintAccessToken(createActiveUser())
        val (comboIds, _) = seedProduct()
        addCartItem(token, comboIds[0], 1)
        updateSaleStatus(comboIds[0], SaleStatus.SUSPENDED)

        assertEquals("SALE_SUSPENDED", itemStatusOf(token), "판매 중지 상품은 SALE_SUSPENDED여야 한다")
    }

    @Test
    fun `판매 종료 상품은 SALE_ENDED다`() {
        val token = mintAccessToken(createActiveUser())
        val (comboIds, _) = seedProduct()
        addCartItem(token, comboIds[0], 1)
        updateSaleStatus(comboIds[0], SaleStatus.ENDED)

        assertEquals("SALE_ENDED", itemStatusOf(token), "판매 종료 상품은 SALE_ENDED여야 한다")
    }

    @Test
    fun `셀러가 영업중이 아니면 SELLER_UNAVAILABLE이다`() {
        val token = mintAccessToken(createActiveUser())
        val (comboIds, _) = seedProduct()
        addCartItem(token, comboIds[0], 1)
        updateSellerStatus(SellerStatus.PAUSED)

        assertEquals("SELLER_UNAVAILABLE", itemStatusOf(token), "셀러가 ACTIVE가 아니면 SELLER_UNAVAILABLE이어야 한다")
    }

    @Test
    fun `상품이 OUT_OF_STOCK이어도 조합에 재고가 있으면 ACTIVE다`() {
        val token = mintAccessToken(createActiveUser())
        val (comboIds, _) = seedProduct(stock = 5)
        addCartItem(token, comboIds[0], 1)
        updateSaleStatus(comboIds[0], SaleStatus.OUT_OF_STOCK)

        assertEquals("ACTIVE", itemStatusOf(token), "품절 판정은 조합 재고 기준이라 상품 sale_status를 보면 안 된다")
    }

    @Test
    fun `셀러 상태는 상품 상태보다 우선한다`() {
        val token = mintAccessToken(createActiveUser())
        val (comboIds, _) = seedProduct()
        addCartItem(token, comboIds[0], 1)
        updateSaleStatus(comboIds[0], SaleStatus.ENDED)
        updateSellerStatus(SellerStatus.WITHDRAWN)

        assertEquals("SELLER_UNAVAILABLE", itemStatusOf(token), "둘 다 걸리면 최상위인 SELLER_UNAVAILABLE이어야 한다")
    }

    @Test
    fun `판매 종료는 옵션 비활성보다 우선한다`() {
        val token = mintAccessToken(createActiveUser())
        val (comboIds, _) = seedProduct()
        addCartItem(token, comboIds[0], 1)
        deactivateOption(comboIds[0])
        updateSaleStatus(comboIds[0], SaleStatus.ENDED)

        assertEquals("SALE_ENDED", itemStatusOf(token), "둘 다 걸리면 선언 순서가 위인 SALE_ENDED여야 한다")
    }

    @Test
    fun `품절보다 옵션 비활성이 우선한다`() {
        val token = mintAccessToken(createActiveUser())
        val (comboIds, _) = seedProduct(stock = 5)
        addCartItem(token, comboIds[0], 1)
        updateStock(comboIds[0], 0)
        deactivateOption(comboIds[0])

        assertEquals("OPTION_UNAVAILABLE", itemStatusOf(token), "둘 다 걸리면 선언 순서가 위인 OPTION_UNAVAILABLE이어야 한다")
    }

    // ---------- 단가와 할인 기간 ----------

    /** 픽스처를 라인 기준(15)과 상품 기준(20)이 갈리는 값으로 잡아 할인율을 두 단가로 다시 내려는 변경이 걸리게 함. */
    @Test
    fun `할인 기간 안이면 원가와 단가에 옵션 추가금이 함께 붙는다`() {
        val token = mintAccessToken(createActiveUser())
        val (comboIds, _) =
            seedProduct(
                regularPrice = 30_000L,
                discountPrice = 24_000L,
                discountStartAt = LocalDateTime.now().minusDays(1),
                discountEndAt = LocalDateTime.now().plusDays(1),
                additionalPrice = 10_000L,
            )
        addCartItem(token, comboIds[0], 1)

        val body = getCart(token)

        assertEquals(40_000L, readLong(body, firstItem("originalPrice")), "할인 전 단가는 정가 30000에 옵션 추가금 10000을 더한 40000이어야 한다")
        assertEquals(34_000L, readLong(body, firstItem("price")), "할인가 24000에 옵션 추가금 10000을 더한 34000이어야 한다")
        assertEquals(
            20,
            JsonPath.read<Int>(body, firstItem("discountRate")),
            "할인율은 상품 정가 기준 20이어야 한다. 추가금을 섞어 15로 내면 같은 상품이 옵션마다 다른 할인율로 보인다",
        )
    }

    @Test
    fun `할인 기간이 지났으면 정가로 단가를 낸다`() {
        val token = mintAccessToken(createActiveUser())
        val (comboIds, _) =
            seedProduct(
                regularPrice = 19_000L,
                discountPrice = 17_100L,
                discountStartAt = LocalDateTime.now().minusDays(3),
                discountEndAt = LocalDateTime.now().minusDays(1),
                additionalPrice = 1_000L,
            )
        addCartItem(token, comboIds[0], 1)

        val body = getCart(token)

        assertEquals(20_000, JsonPath.read<Int>(body, firstItem("price")), "기간이 끝났으면 정가 19000에 추가금 1000을 더해야 한다")
        assertEquals(20_000, JsonPath.read<Int>(body, firstItem("originalPrice")), "할인이 없으면 원가와 단가가 같아야 한다")
        assertNull(JsonPath.read<Int?>(body, firstItem("discountRate")), "적용 중인 할인이 없으면 discountRate는 null이어야 한다")
    }

    @Test
    fun `할인 시작 전이면 정가로 단가를 낸다`() {
        val token = mintAccessToken(createActiveUser())
        val (comboIds, _) =
            seedProduct(
                regularPrice = 19_000L,
                discountPrice = 17_100L,
                discountStartAt = LocalDateTime.now().plusDays(1),
                discountEndAt = LocalDateTime.now().plusDays(3),
            )
        addCartItem(token, comboIds[0], 1)

        assertEquals(19_000, JsonPath.read<Int>(getCart(token), firstItem("price")), "시작 전 할인은 아직 적용하면 안 된다")
    }

    @Test
    fun `종료일이 없는 무기한 할인도 적용한다`() {
        val token = mintAccessToken(createActiveUser())
        val (comboIds, _) =
            seedProduct(
                regularPrice = 19_000L,
                discountPrice = 17_100L,
                discountStartAt = LocalDateTime.now().minusDays(1),
                discountEndAt = null,
                additionalPrice = 1_000L,
            )
        addCartItem(token, comboIds[0], 1)

        assertEquals(18_100, JsonPath.read<Int>(getCart(token), firstItem("price")), "종료일 null은 경계가 없다는 뜻이라 할인가를 써야 한다")
    }

    // ---------- 표시 필드 ----------

    @Test
    fun `remainingStock은 20을 넘어도 마스킹하지 않는다`() {
        val token = mintAccessToken(createActiveUser())
        val (comboIds, _) = seedProduct(stock = 37)
        addCartItem(token, comboIds[0], 1)

        val body = getCart(token)

        assertEquals(37, JsonPath.read<Int>(body, firstItem("remainingStock")), "상품 옵션 조회의 20 임계 마스킹을 장바구니에 적용하면 안 된다")
    }

    @Test
    fun `selectedOptions는 옵션 조합 이름을 그대로 쓴다`() {
        val token = mintAccessToken(createActiveUser())
        val (comboIds, publicId) = seedProduct(optionNames = listOf("소고기 / 500g", "닭고기 / 1kg"))
        val cartItemId = readLong(addCartItem(token, comboIds[1], 2), "$.data.cartItemId")

        val body = getCart(token)

        assertEquals("닭고기 / 1kg", JsonPath.read<String>(body, firstItem("selectedOptions")), "표시 문구는 조합 name이어야 한다")
        assertEquals(cartItemId, readLong(body, firstItem("cartItemId")), "조회의 cartItemId는 담기가 돌려준 라인 id와 같아야 한다")
        assertEquals(publicId, JsonPath.read<String>(body, firstItem("productId")), "productId는 내부 id가 아니라 publicId여야 한다")
        assertEquals("강아지 사료 1kg", JsonPath.read<String>(body, firstItem("productName")), "상품명이 실려야 한다")
        assertEquals(2, JsonPath.read<Int>(body, firstItem("quantity")), "담아둔 수량이 실려야 한다")
        assertEquals(comboIds[1], readLong(body, firstItem("optionCombinationId")), "담긴 조합 id가 실려야 한다")
    }

    @Test
    fun `thumbnail은 대표 이미지 키를 URL로 바꿔 내려준다`() {
        val token = mintAccessToken(createActiveUser())
        val (comboIds, _) = seedProduct(imageKey = thumbnailKey)
        addCartItem(token, comboIds[0], 1)

        val body = getCart(token)

        assertEquals(
            "https://fake-cdn.local/$thumbnailKey",
            JsonPath.read<String>(body, firstItem("thumbnail")),
            "S3 key가 아니라 URL을 내려야 한다",
        )
    }

    @Test
    fun `대표 이미지가 없으면 thumbnail은 null이다`() {
        val token = mintAccessToken(createActiveUser())
        val (comboIds, _) = seedProduct(imageKey = null)
        addCartItem(token, comboIds[0], 1)

        assertNull(JsonPath.read<String?>(getCart(token), firstItem("thumbnail")), "키가 없으면 null을 내려 클라이언트가 폴백하게 둔다")
    }

    // ---------- 계약 위반 상태 방어 ----------

    @Test
    fun `카탈로그 행이 없는 항목은 빼고 나머지를 정상 응답한다`() {
        val buyerId = createActiveUser()
        val token = mintAccessToken(buyerId)
        val (comboIds, _) = seedProduct()
        addCartItem(token, comboIds[0], 2)
        insertOrphanCartItem(buyerId, quantity = 5)

        val body = getCart(token)

        assertEquals(1, JsonPath.read<List<*>>(body, "$.data.sellerGroups[0].items").size, "재료가 없는 항목은 빼고 나머지는 그려야 한다")
        assertEquals(2, JsonPath.read<Int>(body, "$.data.cartItemCount"), "뺀 항목의 수량은 뱃지에도 안 잡혀야 화면과 어긋나지 않는다")
    }

    @Test
    fun `검수가 풀린 상품의 항목은 빼고 나머지를 정상 응답한다`() {
        val token = mintAccessToken(createActiveUser())
        val (kept, _) = seedProduct(productName = "강아지 사료 1kg")
        val (revoked, _) = seedProduct(productName = "고양이 간식")
        addCartItem(token, kept[0], 2)
        addCartItem(token, revoked[0], 3)
        updateInspectionStatus(revoked[0], InspectionStatus.PENDING)

        val body = getCart(token)

        assertEquals(
            listOf("강아지 사료 1kg"),
            JsonPath.read<List<String>>(body, "$.data.sellerGroups[*].items[*].productName"),
            "담기에서 없는 상품으로 다루는 것을 조회에서 ACTIVE로 보여주면 안 된다",
        )
        assertEquals(2, JsonPath.read<Int>(body, "$.data.cartItemCount"), "뺀 항목의 수량은 뱃지에도 안 잡혀야 한다")
    }

    // ---------- 응답 필드 집합 ----------

    /**
     * 의도적으로 뺀 필드(isSoldOut·isSelectable·소계·배송비·총액)가 슬쩍 늘어나는 것을 잡음.
     */
    @Test
    fun `조회 응답 필드 집합은 계약대로 고정이다`() {
        val token = mintAccessToken(createActiveUser())
        val (comboIds, _) =
            seedProduct(
                discountPrice = 17_100L,
                discountStartAt = LocalDateTime.now().minusDays(1),
            )
        addCartItem(token, comboIds[0], 1)

        val body = getCart(token)

        assertEquals(listOf("cartItemCount", "sellerGroups"), keysOf(body, "$.data"), "최상위에 금액 합계 필드를 만들면 안 된다")
        assertEquals(
            listOf("baseShippingFee", "freeShippingThreshold", "items", "sellerId", "storeName"),
            keysOf(body, "$.data.sellerGroups[0]"),
            "셀러 그룹 필드가 계약과 어긋난다",
        )
        assertEquals(
            listOf(
                "cartItemId",
                "discountRate",
                "isOrderable",
                "itemStatus",
                "optionCombinationId",
                "originalPrice",
                "price",
                "productId",
                "productName",
                "quantity",
                "remainingStock",
                "selectedOptions",
                "thumbnail",
            ),
            keysOf(body, "$.data.sellerGroups[0].items[0]"),
            "항목 필드가 계약과 어긋난다. 파생 필드는 itemStatus와 isOrderable 둘뿐이다",
        )
    }

    // ---------- 수정·삭제 HTTP 헬퍼 ----------

    /** 생략과 null 명시를 가르려고 키 자체를 안 넣음. */
    private fun updateJson(
        quantity: Int? = null,
        optionCombinationId: Long? = null,
    ): String =
        listOfNotNull(
            quantity?.let { """"quantity": $it""" },
            optionCombinationId?.let { """"optionCombinationId": $it""" },
        ).joinToString(", ", "{", "}")

    private fun patchCartItem(
        token: String,
        cartItemId: Long,
        body: String,
    ): MockHttpServletRequestBuilder =
        patch("/api/v1/carts/items/$cartItemId")
            .bearer(token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body)

    /** PATCH 후 200을 확인하고 응답 본문을 돌려줌. */
    private fun updateCartItem(
        token: String,
        cartItemId: Long,
        quantity: Int? = null,
        optionCombinationId: Long? = null,
    ): String =
        mockMvc
            .perform(patchCartItem(token, cartItemId, updateJson(quantity, optionCombinationId)))
            .andExpect(status().isOk)
            .andReturn()
            .response
            .getContentAsString(Charsets.UTF_8)

    private fun assertUpdateError(
        token: String,
        cartItemId: Long,
        body: String,
        httpStatus: Int,
        errorCode: Int,
    ) {
        mockMvc
            .perform(patchCartItem(token, cartItemId, body))
            .andExpect(status().`is`(httpStatus))
            .andExpect(jsonPath("$.errorCode").value(errorCode))
    }

    /** POST 후 생성된 항목 id를 돌려줌. */
    private fun addCartItemId(
        token: String,
        comboId: Long,
        quantity: Int,
    ): Long = readLong(addCartItem(token, comboId, quantity), "$.data.cartItemId")

    /** 조회 응답에 실린 순서 그대로의 항목 id 목록. 정렬 단언용. */
    private fun cartItemIdsInOrder(token: String): List<Long> =
        JsonPath
            .read<List<Any>>(getCart(token), "$.data.sellerGroups[*].items[*].cartItemId")
            .map { (it as Number).toLong() }

    private fun optionCombinationIdOf(cartItemId: Long): Long? =
        em
            .createQuery("select ci.optionCombinationId from CartItem ci where ci.id = :id", java.lang.Long::class.java)
            .setParameter("id", cartItemId)
            .resultList
            .firstOrNull()
            ?.toLong()

    // ---------- 수정 성공 ----------

    @Test
    fun `수량 변경은 누적이 아니라 대입이다`() {
        val token = mintAccessToken(createActiveUser())
        val (comboIds, _) = seedProduct()
        val cartItemId = addCartItemId(token, comboIds[0], 3)

        val body = updateCartItem(token, cartItemId, quantity = 2)

        assertEquals(2, JsonPath.read<Int>(body, "$.data.quantity"), "담기의 누적과 달리 수량 변경은 요청 값으로 갈아치워야 한다")
        assertEquals(cartItemId, readLong(body, "$.data.cartItemId"), "병합이 없으면 요청 경로의 id가 그대로 살아남아야 한다")
        assertEquals(false, JsonPath.read<Boolean>(body, "$.data.merged"), "수량만 바꾸면 병합이 아니다")
    }

    @Test
    fun `안 담긴 조합으로 바꾸면 병합 없이 같은 줄이 유지된다`() {
        val token = mintAccessToken(createActiveUser())
        val (comboIds, _) = seedProduct(optionNames = listOf("옵션 1", "옵션 2"))
        val cartItemId = addCartItemId(token, comboIds[0], 3)

        val body = updateCartItem(token, cartItemId, optionCombinationId = comboIds[1])

        assertEquals(false, JsonPath.read<Boolean>(body, "$.data.merged"), "목적지 줄이 없으면 병합이 아니다")
        assertEquals(cartItemId, readLong(body, "$.data.cartItemId"), "병합이 아니면 cartItemId가 바뀌면 안 된다")
        assertEquals(comboIds[1], readLong(body, "$.data.optionCombinationId"), "옵션 조합이 갈아끼워져야 한다")
        assertEquals(3, JsonPath.read<Int>(body, "$.data.quantity"), "옵션만 바꾸면 수량은 그대로다")
        assertEquals(comboIds[1], optionCombinationIdOf(cartItemId), "같은 행의 조합이 바뀌어야 한다. 삭제 후 재생성이면 id가 달라진다")
    }

    @Test
    fun `현재와 같은 조합 요청은 수량만 적용하고 200이다`() {
        val token = mintAccessToken(createActiveUser())
        val (comboIds, _) = seedProduct()
        val cartItemId = addCartItemId(token, comboIds[0], 3)

        val body = updateCartItem(token, cartItemId, quantity = 5, optionCombinationId = comboIds[0])

        assertEquals(false, JsonPath.read<Boolean>(body, "$.data.merged"), "자기 자신은 병합 대상에서 빠져야 한다")
        assertEquals(cartItemId, readLong(body, "$.data.cartItemId"), "자기 줄이 그대로 살아남아야 한다")
        assertEquals(5, JsonPath.read<Int>(body, "$.data.quantity"), "수량은 적용되어야 한다")
        assertEquals(1L, cartItemRowCount(), "자기 자신 요청이 라인을 늘리면 안 된다")
    }

    @Test
    fun `이미 담긴 조합으로 바꾸면 병합하고 살아남은 줄의 id를 준다`() {
        val token = mintAccessToken(createActiveUser())
        val (comboIds, _) = seedProduct(optionNames = listOf("옵션 1", "옵션 2"))
        val destinationId = addCartItemId(token, comboIds[0], 2)
        val sourceId = addCartItemId(token, comboIds[1], 3)

        val body = updateCartItem(token, sourceId, optionCombinationId = comboIds[0])

        assertEquals(true, JsonPath.read<Boolean>(body, "$.data.merged"), "이미 담긴 조합으로 바꾸면 병합이다")
        assertEquals(destinationId, readLong(body, "$.data.cartItemId"), "응답 id는 요청 경로가 아니라 살아남은 목적지 줄이어야 한다")
        assertEquals(5, JsonPath.read<Int>(body, "$.data.quantity"), "목적지 수량에 원본 수량이 더해져야 한다")
        assertEquals(comboIds[0], readLong(body, "$.data.optionCombinationId"), "살아남은 줄의 조합이어야 한다")
        assertEquals(1L, cartItemRowCount(), "원본 줄이 지워져 라인이 하나로 줄어야 한다")
        assertNull(optionCombinationIdOf(sourceId), "원본 줄은 물리 삭제되어야 한다")
    }

    /**
     * 원본 줄을 목적지 값으로 덮은 뒤 지우면 하이버네이트가 UPDATE를 DELETE보다 먼저 내보내
     * uk_cart_items_cart_id_option_combination_id에 걸려 500이 난다. 이 테스트가 그 순서를 지킨다.
     */
    @Test
    fun `병합은 UNIQUE 제약에 걸리지 않는다`() {
        val token = mintAccessToken(createActiveUser())
        val (comboIds, _) = seedProduct(optionNames = listOf("옵션 1", "옵션 2"))
        val destinationId = addCartItemId(token, comboIds[0], 1)
        val sourceId = addCartItemId(token, comboIds[1], 1)

        updateCartItem(token, sourceId, optionCombinationId = comboIds[0])

        assertEquals(comboIds[0], optionCombinationIdOf(destinationId), "목적지 줄의 조합은 그대로여야 한다")
        assertEquals(1L, cartItemRowCount(), "병합 뒤 남는 라인은 하나여야 한다")
    }

    @Test
    fun `수량과 옵션이 함께 오면 수량을 원본 줄에 먼저 적용한 뒤 병합한다`() {
        val token = mintAccessToken(createActiveUser())
        val (comboIds, _) = seedProduct(optionNames = listOf("옵션 1", "옵션 2"))
        val destinationId = addCartItemId(token, comboIds[0], 3)
        val sourceId = addCartItemId(token, comboIds[1], 2)

        val body = updateCartItem(token, sourceId, quantity = 5, optionCombinationId = comboIds[0])

        assertEquals(destinationId, readLong(body, "$.data.cartItemId"), "목적지 줄이 살아남아야 한다")
        assertEquals(8, JsonPath.read<Int>(body, "$.data.quantity"), "원본에 5를 먼저 대입한 뒤 더해야 8이다. 원본 수량 2로 더하면 5가 된다")
    }

    @Test
    fun `서로 다른 두 줄을 같은 조합으로 동시에 바꿔도 둘 다 성공하고 한 줄로 합쳐진다`() {
        val token = mintAccessToken(createActiveUser())
        val (comboIds, _) = seedProduct(optionNames = listOf("옵션 1", "옵션 2", "옵션 3"))
        val firstId = addCartItemId(token, comboIds[1], 1)
        val secondId = addCartItemId(token, comboIds[2], 2)

        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        val statuses =
            try {
                listOf(firstId, secondId)
                    .map { id ->
                        pool.submit<Int> {
                            start.await()
                            mockMvc
                                .perform(patchCartItem(token, id, updateJson(optionCombinationId = comboIds[0])))
                                .andReturn()
                                .response
                                .status
                        }
                    }.also { start.countDown() }
                    .map { it.get() }
            } finally {
                pool.shutdown()
            }

        assertEquals(listOf(200, 200), statuses, "carts 행 잠금이 직렬화해 둘 다 성공해야 한다")
        assertEquals(1L, cartItemRowCount(), "무잠금이면 UNIQUE 위반이 나거나 줄이 두 개 남는다")
        assertEquals(3, quantityOf(comboIds[0]), "두 줄의 수량 1+2가 합산되어야 한다")
    }

    // ---------- 수정 검증 순서 ----------

    @Test
    fun `quantity와 optionCombinationId가 둘 다 없으면 90001이다`() {
        val token = mintAccessToken(createActiveUser())
        val (comboIds, _) = seedProduct()
        val cartItemId = addCartItemId(token, comboIds[0], 1)

        assertUpdateError(token, cartItemId, "{}", 400, 90001)
    }

    @Test
    fun `수량이 1 미만이면 90001이다`() {
        val token = mintAccessToken(createActiveUser())
        val (comboIds, _) = seedProduct()
        val cartItemId = addCartItemId(token, comboIds[0], 1)

        assertUpdateError(token, cartItemId, updateJson(quantity = 0), 400, 90001)
    }

    @Test
    fun `수량이 라인당 상한 99를 넘으면 90001이다`() {
        val token = mintAccessToken(createActiveUser())
        val (comboIds, _) = seedProduct(stock = 500)
        val cartItemId = addCartItemId(token, comboIds[0], 1)

        assertUpdateError(token, cartItemId, updateJson(quantity = 100), 400, 90001)
    }

    @Test
    fun `수량 하한 경계인 1은 성공한다`() {
        val token = mintAccessToken(createActiveUser())
        val (comboIds, _) = seedProduct()
        val cartItemId = addCartItemId(token, comboIds[0], 3)

        val body = updateCartItem(token, cartItemId, quantity = 1)

        assertEquals(1, JsonPath.read<Int>(body, "$.data.quantity"), "하한 경계 1은 90001이 아니라 성공이어야 한다")
    }

    @Test
    fun `수량 상한 경계인 99는 성공한다`() {
        val token = mintAccessToken(createActiveUser())
        val (comboIds, _) = seedProduct(stock = 500)
        val cartItemId = addCartItemId(token, comboIds[0], 1)

        val body = updateCartItem(token, cartItemId, quantity = 99)

        assertEquals(99, JsonPath.read<Int>(body, "$.data.quantity"), "상한 경계 99는 90001이 아니라 성공이어야 한다")
    }

    @Test
    fun `요청 형식 위반은 항목 없음보다 먼저다`() {
        val token = mintAccessToken(createActiveUser())

        assertUpdateError(token, 999_999L, "{}", 400, 90001)
    }

    @Test
    fun `수량 하한 위반은 항목 없음보다 먼저다`() {
        val token = mintAccessToken(createActiveUser())

        assertUpdateError(token, 999_999L, updateJson(quantity = 0), 400, 90001)
    }

    @Test
    fun `수량 상한 위반은 본인 것 아님보다 먼저다`() {
        val (comboIds, _) = seedProduct()
        val ownerCartItemId = addCartItemId(mintAccessToken(createActiveUser()), comboIds[0], 1)
        val intruderToken = mintAccessToken(createActiveUser())

        assertUpdateError(intruderToken, ownerCartItemId, updateJson(quantity = 100), 400, 90001)
    }

    @Test
    fun `없는 항목을 수정하면 50205다`() {
        val token = mintAccessToken(createActiveUser())

        assertUpdateError(token, 999_999L, updateJson(quantity = 2), 404, 50205)
    }

    @Test
    fun `남의 항목을 수정하면 50206이다`() {
        val (comboIds, _) = seedProduct()
        val ownerCartItemId = addCartItemId(mintAccessToken(createActiveUser()), comboIds[0], 1)
        val intruderToken = mintAccessToken(createActiveUser())

        assertUpdateError(intruderToken, ownerCartItemId, updateJson(quantity = 2), 403, 50206)
    }

    @Test
    fun `장바구니를 가진 사용자가 남의 항목을 수정해도 50206이다`() {
        val (comboIds, _) = seedProduct(optionNames = listOf("옵션 1", "옵션 2"))
        val ownerCartItemId = addCartItemId(mintAccessToken(createActiveUser()), comboIds[0], 1)
        val intruderToken = mintAccessToken(createActiveUser())
        addCartItemId(intruderToken, comboIds[1], 1)

        assertUpdateError(intruderToken, ownerCartItemId, updateJson(quantity = 2), 403, 50206)
    }

    @Test
    fun `본인 것 아님은 대상 옵션 조합 없음보다 먼저다`() {
        val (comboIds, _) = seedProduct()
        val ownerCartItemId = addCartItemId(mintAccessToken(createActiveUser()), comboIds[0], 1)
        val intruderToken = mintAccessToken(createActiveUser())

        assertUpdateError(intruderToken, ownerCartItemId, updateJson(optionCombinationId = 999_999L), 403, 50206)
    }

    /** 담기가 50207로 막는 것을 수정으로 우회하지 못해야 함. findItems는 검수를 안 걸러서 포트가 갈림. */
    @Test
    fun `검수 승인이 풀린 항목은 수량을 늘릴 수 없다`() {
        val token = mintAccessToken(createActiveUser())
        val (comboIds, _) = seedProduct()
        val cartItemId = addCartItemId(token, comboIds[0], 2)
        updateInspectionStatus(comboIds[0], InspectionStatus.PENDING)

        assertUpdateError(token, cartItemId, updateJson(quantity = 5), 404, 50207)
    }

    @Test
    fun `검수 승인이 풀린 조합으로는 바꿀 수 없다`() {
        val token = mintAccessToken(createActiveUser())
        val (comboIds, _) = seedProduct(optionNames = listOf("옵션 1", "옵션 2"))
        val cartItemId = addCartItemId(token, comboIds[0], 2)
        updateInspectionStatus(comboIds[1], InspectionStatus.PENDING)

        assertUpdateError(token, cartItemId, updateJson(optionCombinationId = comboIds[1]), 404, 50207)
    }

    @Test
    fun `검수 승인이 풀려도 수량을 줄이는 것은 막지 않는다`() {
        val token = mintAccessToken(createActiveUser())
        val (comboIds, _) = seedProduct()
        val cartItemId = addCartItemId(token, comboIds[0], 5)
        updateInspectionStatus(comboIds[0], InspectionStatus.PENDING)

        val body = updateCartItem(token, cartItemId, quantity = 2)

        assertEquals(2, JsonPath.read<Int>(body, "$.data.quantity"), "깎는 방향은 카탈로그를 아예 안 보므로 검수 상태와 무관하다")
    }

    @Test
    fun `없는 옵션 조합으로 바꾸면 50207이다`() {
        val token = mintAccessToken(createActiveUser())
        val (comboIds, _) = seedProduct()
        val cartItemId = addCartItemId(token, comboIds[0], 1)

        assertUpdateError(token, cartItemId, updateJson(optionCombinationId = 999_999L), 404, 50207)
    }

    @Test
    fun `다른 상품의 조합으로 바꾸면 90001이다`() {
        val token = mintAccessToken(createActiveUser())
        val (comboIds, _) = seedProduct()
        val (otherComboIds, _) = seedProduct(productName = "고양이 사료 1kg")
        val cartItemId = addCartItemId(token, comboIds[0], 1)

        assertUpdateError(token, cartItemId, updateJson(optionCombinationId = otherComboIds[0]), 400, 90001)
    }

    @Test
    fun `다른 상품 판정은 판매 불가보다 먼저다`() {
        val token = mintAccessToken(createActiveUser())
        val (comboIds, _) = seedProduct()
        val (otherComboIds, _) = seedProduct(productName = "고양이 사료 1kg")
        val cartItemId = addCartItemId(token, comboIds[0], 1)
        updateSaleStatus(otherComboIds[0], SaleStatus.SUSPENDED)

        assertUpdateError(token, cartItemId, updateJson(optionCombinationId = otherComboIds[0]), 400, 90001)
    }

    @Test
    fun `살 수 없는 조합으로 바꾸면 50202다`() {
        val token = mintAccessToken(createActiveUser())
        val (comboIds, _) = seedProduct(optionNames = listOf("옵션 1", "옵션 2"))
        val cartItemId = addCartItemId(token, comboIds[0], 1)
        deactivateOption(comboIds[1])

        assertUpdateError(token, cartItemId, updateJson(optionCombinationId = comboIds[1]), 409, 50202)
    }

    @Test
    fun `살 수 없는 항목의 수량을 늘리면 50202다`() {
        val token = mintAccessToken(createActiveUser())
        val (comboIds, _) = seedProduct()
        val cartItemId = addCartItemId(token, comboIds[0], 2)
        updateSaleStatus(comboIds[0], SaleStatus.SUSPENDED)

        assertUpdateError(token, cartItemId, updateJson(quantity = 3), 409, 50202)
    }

    @Test
    fun `셀러가 영업중이 아니면 수량 증가도 50202다`() {
        val token = mintAccessToken(createActiveUser())
        val (comboIds, _) = seedProduct()
        val cartItemId = addCartItemId(token, comboIds[0], 2)
        updateSellerStatus(SellerStatus.PAUSED)

        assertUpdateError(token, cartItemId, updateJson(quantity = 3), 409, 50202)
    }

    @Test
    fun `판매 불가는 병합 합산 99 초과보다 먼저다`() {
        val token = mintAccessToken(createActiveUser())
        val (comboIds, _) = seedProduct(stock = 500, optionNames = listOf("옵션 1", "옵션 2"))
        addCartItemId(token, comboIds[0], 60)
        val sourceId = addCartItemId(token, comboIds[1], 50)
        updateSaleStatus(comboIds[0], SaleStatus.SUSPENDED)

        assertUpdateError(token, sourceId, updateJson(optionCombinationId = comboIds[0]), 409, 50202)
    }

    @Test
    fun `병합 합산 수량이 99를 넘으면 90001이고 병합하지 않는다`() {
        val token = mintAccessToken(createActiveUser())
        val (comboIds, _) = seedProduct(stock = 500, optionNames = listOf("옵션 1", "옵션 2"))
        addCartItemId(token, comboIds[0], 60)
        val sourceId = addCartItemId(token, comboIds[1], 50)

        assertUpdateError(token, sourceId, updateJson(optionCombinationId = comboIds[0]), 400, 90001)
        assertEquals(2L, cartItemRowCount(), "차단된 병합은 원본 줄을 지우면 안 된다")
        assertEquals(comboIds[1], optionCombinationIdOf(sourceId), "차단된 병합은 원본 줄의 조합을 바꾸면 안 된다")
    }

    @Test
    fun `병합 합산 99 초과는 재고 부족보다 먼저다`() {
        val token = mintAccessToken(createActiveUser())
        val (comboIds, _) = seedProduct(stock = 100, optionNames = listOf("옵션 1", "옵션 2"))
        addCartItemId(token, comboIds[0], 60)
        val sourceId = addCartItemId(token, comboIds[1], 50)

        // 합산 110은 라인 상한 99와 잔여 재고 100을 동시에 넘음
        assertUpdateError(token, sourceId, updateJson(optionCombinationId = comboIds[0]), 400, 90001)
    }

    @Test
    fun `수량을 늘려 잔여 재고를 넘으면 50201이다`() {
        val token = mintAccessToken(createActiveUser())
        val (comboIds, _) = seedProduct(stock = 10)
        val cartItemId = addCartItemId(token, comboIds[0], 3)

        assertUpdateError(token, cartItemId, updateJson(quantity = 11), 409, 50201)
    }

    @Test
    fun `잔여 재고와 같은 수량까지는 늘릴 수 있다 - 경계`() {
        val token = mintAccessToken(createActiveUser())
        val (comboIds, _) = seedProduct(stock = 10)
        val cartItemId = addCartItemId(token, comboIds[0], 3)

        val body = updateCartItem(token, cartItemId, quantity = 10)

        assertEquals(10, JsonPath.read<Int>(body, "$.data.quantity"), "재고와 같은 수량은 담기처럼 수정에서도 허용해야 한다")
    }

    @Test
    fun `병합 합산이 잔여 재고를 넘으면 50201이고 병합하지 않는다`() {
        val token = mintAccessToken(createActiveUser())
        val (comboIds, _) = seedProduct(stock = 10, optionNames = listOf("옵션 1", "옵션 2"))
        addCartItemId(token, comboIds[0], 6)
        val sourceId = addCartItemId(token, comboIds[1], 6)

        assertUpdateError(token, sourceId, updateJson(optionCombinationId = comboIds[0]), 409, 50201)
        assertEquals(2L, cartItemRowCount(), "차단된 병합은 원본 줄을 지우면 안 된다")
    }

    @Test
    fun `병합 재고는 원본 조합이 아니라 목적지 조합의 재고로 판정한다`() {
        val token = mintAccessToken(createActiveUser())
        val (comboIds, _) = seedProduct(stock = 10, optionNames = listOf("옵션 1", "옵션 2"))
        addCartItemId(token, comboIds[0], 1)
        val sourceId = addCartItemId(token, comboIds[1], 3)
        // 합산 4는 목적지 재고 2를 넘고 원본 재고 10은 안 넘음. 원본 재고로 판정하면 통과해 버린다
        updateStock(comboIds[0], 2)

        assertUpdateError(token, sourceId, updateJson(optionCombinationId = comboIds[0]), 409, 50201)
        assertEquals(2L, cartItemRowCount(), "차단된 병합은 원본 줄을 지우면 안 된다")
    }

    // ---------- 수량 감소는 검증하지 않는다 ----------

    @Test
    fun `수량을 줄이면 잔여 재고보다 많아도 통과한다`() {
        val token = mintAccessToken(createActiveUser())
        val (comboIds, _) = seedProduct(stock = 10)
        val cartItemId = addCartItemId(token, comboIds[0], 8)
        updateStock(comboIds[0], 1)

        val body = updateCartItem(token, cartItemId, quantity = 5)

        assertEquals(5, JsonPath.read<Int>(body, "$.data.quantity"), "줄이는 방향은 재고를 보지 않아야 한다")
    }

    @Test
    fun `수량을 줄이면 판매 불가 상태여도 통과한다`() {
        val token = mintAccessToken(createActiveUser())
        val (comboIds, _) = seedProduct()
        val cartItemId = addCartItemId(token, comboIds[0], 5)
        updateSaleStatus(comboIds[0], SaleStatus.ENDED)

        val body = updateCartItem(token, cartItemId, quantity = 2)

        assertEquals(2, JsonPath.read<Int>(body, "$.data.quantity"), "줄이는 방향은 판매 상태를 보지 않아야 한다")
    }

    @Test
    fun `수량을 그대로 두면 판매 불가 상태여도 통과한다`() {
        val token = mintAccessToken(createActiveUser())
        val (comboIds, _) = seedProduct()
        val cartItemId = addCartItemId(token, comboIds[0], 5)
        updateSaleStatus(comboIds[0], SaleStatus.ENDED)

        val body = updateCartItem(token, cartItemId, optionCombinationId = comboIds[0])

        assertEquals(5, JsonPath.read<Int>(body, "$.data.quantity"), "늘리지 않는 요청은 판매 상태를 보지 않아야 한다")
    }

    @Test
    fun `수량을 그대로 명시하면 잔여 재고보다 많아도 통과한다`() {
        val token = mintAccessToken(createActiveUser())
        val (comboIds, _) = seedProduct(stock = 10)
        val cartItemId = addCartItemId(token, comboIds[0], 8)
        updateStock(comboIds[0], 1)

        val body = updateCartItem(token, cartItemId, quantity = 8)

        assertEquals(8, JsonPath.read<Int>(body, "$.data.quantity"), "같은 값 재전송은 늘리는 요청이 아니라 재고를 보지 않아야 한다")
    }

    @Test
    fun `현재 조합이 판매 불가여도 수량 증가 없이 산 조합으로 바꿀 수 있다`() {
        val token = mintAccessToken(createActiveUser())
        val (comboIds, _) = seedProduct(optionNames = listOf("옵션 1", "옵션 2"))
        val cartItemId = addCartItemId(token, comboIds[0], 3)
        deactivateOption(comboIds[0])

        val body = updateCartItem(token, cartItemId, optionCombinationId = comboIds[1])

        assertEquals(comboIds[1], readLong(body, "$.data.optionCombinationId"), "판매 가능성 검증 대상은 변경 대상 조합이라 현재 조합이 죽어도 갈아탈 수 있어야 한다")
    }

    @Test
    fun `현재 조합이 판매 불가여도 산 조합으로 바꾸면서 수량을 늘릴 수 있다`() {
        val token = mintAccessToken(createActiveUser())
        val (comboIds, _) = seedProduct(optionNames = listOf("옵션 1", "옵션 2"))
        val cartItemId = addCartItemId(token, comboIds[0], 2)
        deactivateOption(comboIds[0])

        val body = updateCartItem(token, cartItemId, quantity = 5, optionCombinationId = comboIds[1])

        assertEquals(5, JsonPath.read<Int>(body, "$.data.quantity"), "떠나는 조합의 상태는 수량 증가를 막지 않아야 한다")
        assertEquals(comboIds[1], readLong(body, "$.data.optionCombinationId"), "산 조합으로 갈아탄 채 수량이 적용되어야 한다")
    }

    // ---------- 수정·삭제 후 cartItemCount ----------

    @Test
    fun `수정 응답의 cartItemCount는 품목 종류 수가 아니라 수량 합계다`() {
        val token = mintAccessToken(createActiveUser())
        val (comboIds, _) = seedProduct(optionNames = listOf("옵션 1", "옵션 2"))
        val cartItemId = addCartItemId(token, comboIds[0], 2)
        addCartItemId(token, comboIds[1], 3)

        val body = updateCartItem(token, cartItemId, quantity = 4)

        assertEquals(7, JsonPath.read<Int>(body, "$.data.cartItemCount"), "4와 3의 합인 7이어야 한다. 종류 수면 2가 된다")
    }

    @Test
    fun `병합 후 cartItemCount는 합쳐진 수량을 그대로 센다`() {
        val token = mintAccessToken(createActiveUser())
        val (comboIds, _) = seedProduct(optionNames = listOf("옵션 1", "옵션 2"))
        addCartItemId(token, comboIds[0], 2)
        val sourceId = addCartItemId(token, comboIds[1], 3)

        val body = updateCartItem(token, sourceId, optionCombinationId = comboIds[0])

        assertEquals(5, JsonPath.read<Int>(body, "$.data.cartItemCount"), "병합은 수량 합계를 바꾸지 않는다")
    }

    // ---------- 수정·삭제 후 조회 정렬 ----------

    @Test
    fun `수량만 바꾼 항목은 조회 자리가 그대로다`() {
        val token = mintAccessToken(createActiveUser())
        val (comboIds, _) = seedProduct(optionNames = listOf("옵션 1", "옵션 2", "옵션 3"))
        val ids = comboIds.map { addCartItemId(token, it, 1) }
        val before = cartItemIdsInOrder(token)

        updateCartItem(token, ids[0], quantity = 9)

        assertEquals(before, cartItemIdsInOrder(token), "createdAt을 갱신하지 않으므로 자리가 움직이면 안 된다")
    }

    @Test
    fun `병합 없이 옵션만 바꾼 항목도 자리가 그대로다`() {
        val token = mintAccessToken(createActiveUser())
        val (comboIds, _) = seedProduct(optionNames = listOf("옵션 1", "옵션 2", "옵션 3", "옵션 4"))
        val ids = comboIds.take(3).map { addCartItemId(token, it, 1) }
        val before = cartItemIdsInOrder(token)

        updateCartItem(token, ids[0], optionCombinationId = comboIds[3])

        assertEquals(before, cartItemIdsInOrder(token), "행을 지우고 다시 만들면 자리가 맨 위로 튄다")
    }

    @Test
    fun `병합이 일어나면 살아남은 목적지 줄의 자리를 따라간다`() {
        val token = mintAccessToken(createActiveUser())
        val (comboIds, _) = seedProduct(optionNames = listOf("옵션 1", "옵션 2", "옵션 3"))
        val ids = comboIds.map { addCartItemId(token, it, 1) }

        updateCartItem(token, ids[2], optionCombinationId = comboIds[0])

        assertEquals(
            listOf(ids[1], ids[0]),
            cartItemIdsInOrder(token),
            "가장 최근에 담은 줄이 사라지고 목적지 줄은 원래 자리인 맨 뒤에 남아야 한다",
        )
    }

    // ---------- 수정·삭제 인증 ----------

    @Test
    fun `미로그인 수정은 401과 20004다`() {
        mockMvc
            .perform(
                patch("/api/v1/carts/items/1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(updateJson(quantity = 2)),
            ).andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.errorCode").value(20004))
    }

    /**
     * 절대 쿼리 수는 요청당 고정 쿼리 때문에 무관한 변경에도 흔들려서 증분만 봄.
     * 전제는 셋. 항목마다 옵션 조합이 다르고, 카탈로그 조회가 IN 한 번이며, 라인 적재가 fetch join이라 항목 수와 무관함.
     * 통계는 프로퍼티가 아니라 런타임에 켬. properties를 바꾸면 컨텍스트가 갈라져 공유 MySQL 컨테이너가 조기 종료될 수 있음.
     */
    @Nested
    inner class CatalogQueryCount {
        private lateinit var statistics: Statistics

        @BeforeEach
        fun enableStatistics() {
            statistics = em.entityManagerFactory.unwrap(SessionFactory::class.java).statistics
            statistics.isStatisticsEnabled = true
        }

        @Test
        fun `품목이 늘어도 조회 쿼리 수는 그대로다`() {
            val token = mintAccessToken(createActiveUser())
            val (comboIds, _) = seedProduct(optionNames = (1..5).map { "옵션 $it" })
            addCartItem(token, comboIds[0], 1)
            val withOneItem = countStatementsOnGet(token)

            (1..4).forEach { addCartItem(token, comboIds[it], 1) }
            val withFiveItems = countStatementsOnGet(token)

            assertEquals(
                withOneItem,
                withFiveItems,
                "항목마다 카탈로그를 단건 조회하면 품목 수만큼 쿼리가 늘어난다 (1개=$withOneItem, 5개=$withFiveItems)",
            )
        }

        @Test
        fun `조회는 요청당 한 자릿수 쿼리로 끝난다`() {
            val token = mintAccessToken(createActiveUser())
            val (comboIds, _) = seedProduct(optionNames = (1..5).map { "옵션 $it" })
            (0..4).forEach { addCartItem(token, comboIds[it], 1) }

            val statements = countStatementsOnGet(token)

            assertTrue(statements in 1..9, "조회 쿼리 수가 예상 범위를 벗어났다: $statements")
        }

        private fun countStatementsOnGet(token: String): Long {
            statistics.clear()
            getCart(token)
            return statistics.prepareStatementCount
        }
    }
}
