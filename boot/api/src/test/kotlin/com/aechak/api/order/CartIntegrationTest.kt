package com.aechak.api.order

import com.aechak.api.support.IntegrationTestBase
import com.aechak.domain.product.category.Category
import com.aechak.domain.product.option.OptionCombination
import com.aechak.domain.product.product.Product
import com.aechak.domain.product.product.enums.SaleStatus
import com.aechak.domain.seller.seller.Seller
import com.aechak.domain.seller.seller.enums.SellerStatus
import com.jayway.jsonpath.JsonPath
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
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

/**
 * 담기 API 통합 테스트. HTTP 경계부터 실 MySQL까지 태워 담기 흐름과 에러 코드를 고정함.
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

        // 원시 타입으로 받으면 생략이 0으로 새서 50200으로 오판됨
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
}
