package com.aechak.api.order

import com.aechak.api.support.IntegrationTestBase
import com.aechak.common.error.CommonErrorCode
import com.aechak.domain.order.cart.Cart
import com.aechak.domain.product.category.Category
import com.aechak.domain.product.option.OptionCombination
import com.aechak.domain.product.product.Product
import com.aechak.domain.product.product.enums.SaleStatus
import com.aechak.domain.product.version.ProductVersion
import com.aechak.domain.product.version.enums.VersionChangedBy
import com.aechak.domain.seller.seller.Seller
import com.aechak.domain.user.address.DeliveryAddress
import com.aechak.domain.user.user.User
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
import java.util.concurrent.TimeUnit

/**
 * 주문그룹 생성 API 통합. HTTP 경계부터 실 MySQL까지 태워 금액 대조·멱등·재고 원자 차감 계약을 고정함.
 * 재고 차감은 조건부 원자 UPDATE라 Testcontainers 실 DB가 전제(H2 금지).
 */
class OrderGroupIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var context: WebApplicationContext

    @Autowired
    private lateinit var securityFilterChain: FilterChainProxy

    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        mockMvc =
            MockMvcBuilders
                .webAppContextSetup(context)
                .addFilters<DefaultMockMvcBuilder>(securityFilterChain)
                .build()
    }

    // ---------- 픽스처 헬퍼 ----------

    /** 셀러 + 상품 + 옵션조합(+버전)을 심고 옵션조합 id를 돌려줌. */
    private fun seedSellerProduct(
        sellerUserId: Long,
        storeName: String,
        shippingFee: Long,
        price: Long,
        stock: Int,
        sig: String,
        withVersion: Boolean = true,
    ): Long =
        tx.execute {
            em.persist(Seller.open(sellerUserId, storeName, shippingFee))
            val category = Category.create(null, 1, "카테고리-$sig", null, 1)
            em.persist(category)
            val product = Product.register(category, sellerUserId, "상품-$sig", null, null, price, null, null, null)
            em.persist(product)
            if (withVersion) {
                em.persist(
                    ProductVersion.snapshot(
                        product = product,
                        versionNo = 1,
                        nameSnapshot = product.name,
                        priceSnapshot = price,
                        statusSnapshot = SaleStatus.ON_SALE,
                        thumbnailKeySnapshot = "thumb-$sig",
                        changedBy = VersionChangedBy.SELLER,
                    ),
                )
            }
            val combo = OptionCombination.create(product, "기본 / 1개", 0L, stock, "sig-$sig")
            em.persist(combo)
            combo.id
        }!!

    /** 장바구니에 (옵션조합, 수량)을 담고 cartItemId 목록을 돌려줌. */
    private fun seedCart(
        buyerId: Long,
        items: List<Pair<Long, Int>>,
    ): List<Long> =
        tx.execute {
            val cart = Cart.create(buyerId)
            em.persist(cart)
            val cartItems = items.map { (comboId, quantity) -> cart.addItem(comboId, quantity) }
            em.flush()
            cartItems.map { it.id }
        }!!

    private fun seedAddress(userId: Long): Long =
        tx.execute {
            val user = em.find(User::class.java, userId)
            val address =
                DeliveryAddress.register(
                    user = user,
                    receiverName = "홍길동",
                    contactNumber = "01012345678".toByteArray(Charsets.UTF_8),
                    zipCode = "12345",
                    baseAddress = "서울시 애착구 멍냥로 1",
                    detailAddress = "101동 202호",
                    isDefault = true,
                )
            em.persist(address)
            address.id
        }!!

    private fun suspendSale(comboId: Long) {
        tx.execute {
            em
                .createQuery(
                    "update Product p set p.saleStatus = :st, p.updatedAt = CURRENT_TIMESTAMP " +
                        "where p.id = (select oc.product.id from OptionCombination oc where oc.id = :id)",
                ).setParameter("st", SaleStatus.SUSPENDED)
                .setParameter("id", comboId)
                .executeUpdate()
        }
    }

    private fun stockOf(comboId: Long): Int =
        em
            .createQuery("select oc.stockQuantity from OptionCombination oc where oc.id = :id", java.lang.Integer::class.java)
            .setParameter("id", comboId)
            .singleResult
            .toInt()

    private fun orderGroupCount(): Long = countOf("select count(g) from OrderGroup g")

    private fun orderCount(): Long = countOf("select count(o) from Order o")

    private fun orderItemCount(): Long = countOf("select count(i) from OrderItem i")

    private fun cartItemCount(): Long = countOf("select count(ci) from CartItem ci")

    private fun countOf(jpql: String): Long =
        em
            .createQuery(jpql, java.lang.Long::class.java)
            .singleResult
            .toLong()

    // ---------- HTTP 헬퍼 ----------

    private fun MockHttpServletRequestBuilder.bearer(token: String): MockHttpServletRequestBuilder =
        this.header(HttpHeaders.AUTHORIZATION, "Bearer $token")

    private fun orderGroupJson(
        cartItemIds: List<Long>,
        addressId: Long,
        expectedFinalAmount: Long,
        usedPoint: Long = 0,
    ): String =
        """
        {"cartItemIds": $cartItemIds, "deliveryAddressId": $addressId,
         "usedPoint": $usedPoint, "expectedFinalAmount": $expectedFinalAmount}
        """.trimIndent()

    private fun postOrderGroup(
        token: String,
        body: String,
        idempotencyKey: String?,
    ): MockHttpServletRequestBuilder {
        val builder =
            post("/api/v1/order-groups")
                .bearer(token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
        return if (idempotencyKey != null) builder.header("Idempotency-Key", idempotencyKey) else builder
    }

    // ---------- 테스트 ----------

    @Test
    fun `셀러 둘을 한 결제로 묶어 주문그룹을 만들고 재고를 깎는다`() {
        val buyerId = createActiveUser()
        val token = mintAccessToken(buyerId)
        val comboA = seedSellerProduct(71L, "멍멍상회", 3000L, 19000L, 10, "a")
        val comboB = seedSellerProduct(72L, "야옹샵", 2500L, 8000L, 5, "b")
        val cartItemIds = seedCart(buyerId, listOf(comboA to 2, comboB to 1))
        val addressId = seedAddress(buyerId)
        // (19000*2 + 8000) + (3000 + 2500)
        val expected = 46000L + 5500L

        val body =
            mockMvc
                .perform(postOrderGroup(token, orderGroupJson(cartItemIds, addressId, expected), "key-success"))
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.data.totalProductAmount").value(46000))
                .andExpect(jsonPath("$.data.totalShippingFee").value(5500))
                .andExpect(jsonPath("$.data.finalPaymentAmount").value(expected))
                .andExpect(jsonPath("$.data.usedPoint").value(0))
                .andExpect(jsonPath("$.data.expiresAt").isNotEmpty)
                .andReturn()
                .response
                .getContentAsString(Charsets.UTF_8)

        assertEquals(26, JsonPath.read<String>(body, "$.data.orderGroupId").length)
        assertEquals(1L, orderGroupCount())
        assertEquals(2L, orderCount())
        assertEquals(2L, orderItemCount())
        assertEquals(8, stockOf(comboA))
        assertEquals(4, stockOf(comboB))
        assertEquals("PENDING_PAYMENT", em.createQuery("select g.status from OrderGroup g", Any::class.java).singleResult.toString())
        // 장바구니 정리는 결제 확정 이후 — 생성 시점에는 남아 있어야 함
        assertEquals(2L, cartItemCount())
    }

    @Test
    fun `같은 멱등키 재요청은 새로 만들지 않고 최초 결과를 돌려준다`() {
        val buyerId = createActiveUser()
        val token = mintAccessToken(buyerId)
        val combo = seedSellerProduct(71L, "멍멍상회", 3000L, 10000L, 10, "a")
        val cartItemIds = seedCart(buyerId, listOf(combo to 1))
        val addressId = seedAddress(buyerId)
        val json = orderGroupJson(cartItemIds, addressId, 13000L)

        val first = perform201(token, json, "key-replay")
        val second = perform201(token, json, "key-replay")

        assertEquals(
            JsonPath.read<String>(first, "$.data.orderGroupId"),
            JsonPath.read<String>(second, "$.data.orderGroupId"),
        )
        assertEquals(1L, orderGroupCount())
        assertEquals(9, stockOf(combo)) // 두 번 깎이지 않음
    }

    @Test
    fun `남의 멱등키로 재요청하면 거부한다`() {
        val buyerId = createActiveUser()
        val combo = seedSellerProduct(71L, "멍멍상회", 3000L, 10000L, 10, "a")
        val cartItemIds = seedCart(buyerId, listOf(combo to 1))
        val addressId = seedAddress(buyerId)
        perform201(mintAccessToken(buyerId), orderGroupJson(cartItemIds, addressId, 13000L), "key-owner")

        val intruderId = createActiveUser()
        val intruderCartItemIds = seedCart(intruderId, listOf(combo to 1))
        val intruderAddressId = seedAddress(intruderId)

        mockMvc
            .perform(
                postOrderGroup(
                    mintAccessToken(intruderId),
                    orderGroupJson(intruderCartItemIds, intruderAddressId, 13000L),
                    "key-owner",
                ),
            ).andExpect(status().isForbidden)
            .andExpect(jsonPath("$.errorCode").value(CommonErrorCode.IDEMPOTENCY_KEY_ACCESS_DENIED.code))
    }

    @Test
    fun `표시 금액과 서버 계산이 다르면 생성하지 않는다`() {
        val buyerId = createActiveUser()
        val token = mintAccessToken(buyerId)
        val combo = seedSellerProduct(71L, "멍멍상회", 3000L, 10000L, 10, "a")
        val cartItemIds = seedCart(buyerId, listOf(combo to 1))
        val addressId = seedAddress(buyerId)

        mockMvc
            .perform(postOrderGroup(token, orderGroupJson(cartItemIds, addressId, 13000L + 100), "key-mismatch"))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.errorCode").value(50106))

        assertEquals(0L, orderGroupCount())
        assertEquals(10, stockOf(combo)) // 대조 실패는 재고를 건드리기 전
    }

    @Test
    fun `판매 중지된 상품이 끼면 거절한다`() {
        val buyerId = createActiveUser()
        val token = mintAccessToken(buyerId)
        val combo = seedSellerProduct(71L, "멍멍상회", 3000L, 10000L, 10, "a")
        val cartItemIds = seedCart(buyerId, listOf(combo to 1))
        val addressId = seedAddress(buyerId)
        suspendSale(combo)

        mockMvc
            .perform(postOrderGroup(token, orderGroupJson(cartItemIds, addressId, 13000L), "key-suspended"))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.errorCode").value(50104))
    }

    @Test
    fun `상품 버전이 아직 없으면 거절한다`() {
        val buyerId = createActiveUser()
        val token = mintAccessToken(buyerId)
        val combo = seedSellerProduct(71L, "멍멍상회", 3000L, 10000L, 10, "a", withVersion = false)
        val cartItemIds = seedCart(buyerId, listOf(combo to 1))
        val addressId = seedAddress(buyerId)

        mockMvc
            .perform(postOrderGroup(token, orderGroupJson(cartItemIds, addressId, 13000L), "key-no-version"))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.errorCode").value(50105))
    }

    @Test
    fun `적립금 사용은 아직 막혀 있다`() {
        val buyerId = createActiveUser()
        val token = mintAccessToken(buyerId)
        val combo = seedSellerProduct(71L, "멍멍상회", 3000L, 10000L, 10, "a")
        val cartItemIds = seedCart(buyerId, listOf(combo to 1))
        val addressId = seedAddress(buyerId)

        mockMvc
            .perform(postOrderGroup(token, orderGroupJson(cartItemIds, addressId, 12500L, usedPoint = 500), "key-point"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(90001))
    }

    @Test
    fun `멱등키 헤더가 없으면 400`() {
        val buyerId = createActiveUser()
        val token = mintAccessToken(buyerId)
        val combo = seedSellerProduct(71L, "멍멍상회", 3000L, 10000L, 10, "a")
        val cartItemIds = seedCart(buyerId, listOf(combo to 1))
        val addressId = seedAddress(buyerId)

        mockMvc
            .perform(postOrderGroup(token, orderGroupJson(cartItemIds, addressId, 13000L), idempotencyKey = null))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(90001))
    }

    @Test
    fun `남의 배송지로는 주문할 수 없다`() {
        val buyerId = createActiveUser()
        val token = mintAccessToken(buyerId)
        val combo = seedSellerProduct(71L, "멍멍상회", 3000L, 10000L, 10, "a")
        val cartItemIds = seedCart(buyerId, listOf(combo to 1))
        val othersAddressId = seedAddress(createActiveUser())

        mockMvc
            .perform(postOrderGroup(token, orderGroupJson(cartItemIds, othersAddressId, 13000L), "key-address"))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.errorCode").value(30502))
    }

    @Test
    fun `동시 더블클릭은 멱등키 UNIQUE가 심판해 하나만 생성된다`() {
        val buyerId = createActiveUser()
        val token = mintAccessToken(buyerId)
        val combo = seedSellerProduct(71L, "멍멍상회", 3000L, 10000L, 10, "a")
        val cartItemIds = seedCart(buyerId, listOf(combo to 1))
        val addressId = seedAddress(buyerId)
        val json = orderGroupJson(cartItemIds, addressId, 13000L)

        val results = raceTwo { postOrderGroup(token, json, "key-race") }

        results.forEach { assertEquals(201, it.first) }
        assertEquals(
            JsonPath.read<String>(results[0].second, "$.data.orderGroupId"),
            JsonPath.read<String>(results[1].second, "$.data.orderGroupId"),
        )
        assertEquals(1L, orderGroupCount())
        assertEquals(9, stockOf(combo)) // 진 쪽의 차감은 롤백됨
    }

    @Test
    fun `마지막 재고를 두 구매자가 다투면 한 명만 성공한다`() {
        val combo = seedSellerProduct(71L, "멍멍상회", 3000L, 10000L, 1, "a")
        val buyerA = createActiveUser()
        val buyerB = createActiveUser()
        val requests =
            listOf(buyerA, buyerB).mapIndexed { i, buyerId ->
                val cartItemIds = seedCart(buyerId, listOf(combo to 1))
                val addressId = seedAddress(buyerId)
                postOrderGroup(mintAccessToken(buyerId), orderGroupJson(cartItemIds, addressId, 13000L), "key-race-$i")
            }

        val results = raceTwo(requests[0]) { requests[1] }

        val statuses = results.map { it.first }.sorted()
        assertEquals(listOf(201, 409), statuses)
        val loser = results.first { it.first == 409 }
        val loserCode = JsonPath.read<Int>(loser.second, "$.errorCode")
        // 빠른 실패 검증(50104)과 원자 차감 실패(50107) 중 어느 쪽이 잡는지는 도착 순서에 달림
        assertTrue(loserCode == 50104 || loserCode == 50107, "예상 밖 에러코드: $loserCode")
        assertEquals(0, stockOf(combo))
        assertEquals(1L, orderGroupCount())
    }

    // ---------- 동시 실행 헬퍼 ----------

    private fun perform201(
        token: String,
        json: String,
        key: String,
    ): String =
        mockMvc
            .perform(postOrderGroup(token, json, key))
            .andExpect(status().isCreated)
            .andReturn()
            .response
            .getContentAsString(Charsets.UTF_8)

    /** 요청 둘을 동시에 쏘고 (status, body) 쌍을 돌려줌. */
    private fun raceTwo(
        first: MockHttpServletRequestBuilder? = null,
        request: () -> MockHttpServletRequestBuilder,
    ): List<Pair<Int, String>> {
        val pool = Executors.newFixedThreadPool(2)
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        try {
            val futures =
                listOf(first ?: request(), request()).map { builder ->
                    pool.submit<Pair<Int, String>> {
                        ready.countDown()
                        start.await()
                        val response = mockMvc.perform(builder).andReturn().response
                        response.status to response.getContentAsString(Charsets.UTF_8)
                    }
                }
            ready.await()
            start.countDown()
            return futures.map { it.get(30, TimeUnit.SECONDS) }
        } finally {
            pool.shutdown()
        }
    }
}
