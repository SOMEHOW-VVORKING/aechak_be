package com.aechak.api.order

import com.aechak.api.support.IntegrationTestBase
import com.aechak.domain.order.claim.Claim
import com.aechak.domain.order.claim.ClaimItem
import com.aechak.domain.order.claim.enums.ClaimStatus
import com.aechak.domain.order.claim.enums.ClaimType
import com.aechak.domain.order.group.DeliveryAddressSnapshot
import com.aechak.domain.order.group.OrderGroup
import com.aechak.domain.order.order.Order
import com.aechak.domain.order.order.OrderItem
import com.aechak.domain.order.order.enums.ConfirmType
import com.aechak.domain.order.order.enums.OrderStatus
import com.aechak.domain.order.order.enums.TriggeredBy
import com.aechak.domain.order.order.repository.OrderRepository
import com.aechak.domain.product.category.Category
import com.aechak.domain.product.option.OptionCombination
import com.aechak.domain.product.product.Product
import com.aechak.domain.product.product.enums.SaleStatus
import com.aechak.domain.product.version.ProductVersion
import com.aechak.domain.product.version.enums.VersionChangedBy
import com.aechak.domain.seller.seller.Seller
import com.aechak.domain.user.user.User
import com.aechak.infra.persistence.order.order.OrderRepositoryAdapter
import com.jayway.jsonpath.JsonPath
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.http.HttpHeaders
import org.springframework.security.web.FilterChainProxy
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.time.LocalDateTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * 구매자 구매확정 통합. 확정은 비가역이고 적립금·리뷰·정산의 기산점이라
 * 전이 선점과 적립 멱등을 Testcontainers 실 MySQL로 고정한다(H2 금지).
 */
@Import(OrderConfirmIntegrationTest.TransitionRaceConfig::class)
class OrderConfirmIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var context: WebApplicationContext

    @Autowired
    private lateinit var securityFilterChain: FilterChainProxy

    @Autowired
    private lateinit var transitionBarrier: TransitionBarrier

    private lateinit var mockMvc: MockMvc
    private var buyerId = 0L
    private lateinit var token: String
    private var sig = 0

    @BeforeEach
    fun setUp() {
        mockMvc =
            MockMvcBuilders
                .webAppContextSetup(context)
                .addFilters<DefaultMockMvcBuilder>(securityFilterChain)
                .build()
        buyerId = createActiveUser()
        token = mintAccessToken(buyerId)
    }

    @AfterEach
    fun resetBarrier() {
        transitionBarrier.disable()
    }

    @Test
    fun `배송완료 주문을 확정하면 204와 함께 구매확정 시각과 확정 유형이 남는다`() {
        val fixture = seedOrder()

        mockMvc.perform(confirmRequest(token, fixture.orderPublicId)).andExpect(status().isNoContent)

        val order = findOrder(fixture.orderPublicId)
        assertEquals(OrderStatus.PURCHASE_CONFIRMED, order.status)
        assertNotNull(order.purchaseConfirmedAt, "확정 시각이 남아야 한다")
        assertEquals(ConfirmType.MANUAL, order.confirmType, "구매자가 직접 누른 확정은 MANUAL이어야 한다")
    }

    @Test
    fun `확정은 이력 1행을 남기고 발생 주체는 구매자다`() {
        val fixture = seedOrder()

        mockMvc.perform(confirmRequest(token, fixture.orderPublicId)).andExpect(status().isNoContent)

        assertEquals(
            listOf(Triple(OrderStatus.DELIVERED, OrderStatus.PURCHASE_CONFIRMED, TriggeredBy.BUYER)),
            histories(fixture.orderPublicId),
        )
    }

    @Test
    fun `확정하면 할인 안분액을 뺀 상품금액의 0_5퍼센트를 적립하고 잔액과 갱신 시각이 함께 움직인다`() {
        // 10,000 × 2 − 500 = 19,500 → 0.5%는 97.5원이고 1원 미만은 버린다
        val fixture = seedOrder(unitPrice = 10_000L, quantity = 2, discountAllocatedAmount = 500L)
        val balanceBefore = pointBalance(buyerId)
        val updatedAtBefore = userUpdatedAt(buyerId)

        mockMvc.perform(confirmRequest(token, fixture.orderPublicId)).andExpect(status().isNoContent)

        val rewards = rewards(fixture.orderPublicId)
        assertEquals(1, rewards.size, "적립 원장은 1행이어야 한다")
        assertEquals(97L, rewards.single().amount, "배송비를 뺀 상품금액 기준으로 내림 적립해야 한다")
        assertEquals("EARN", rewards.single().transactionType.name)
        assertEquals("ORDER", rewards.single().sourceType)
        assertEquals(fixture.orderId, rewards.single().sourceId, "출처는 확정된 셀러 주문이어야 한다")
        assertEquals(balanceBefore + 97L, pointBalance(buyerId), "잔액 캐시도 함께 올라야 한다")
        assertTrue(
            userUpdatedAt(buyerId).isAfter(updatedAtBefore),
            "잔액 가산은 원자 UPDATE라 갱신 시각을 쿼리에서 함께 SET해야 한다",
        )
    }

    @Test
    fun `적립액이 1원에 못 미치면 확정만 하고 원장은 남기지 않는다`() {
        val fixture = seedOrder(unitPrice = 100L, quantity = 1, discountAllocatedAmount = 0L)

        mockMvc.perform(confirmRequest(token, fixture.orderPublicId)).andExpect(status().isNoContent)

        assertEquals(OrderStatus.PURCHASE_CONFIRMED, findOrder(fixture.orderPublicId).status)
        assertEquals(emptyList<Reward>(), rewards(fixture.orderPublicId), "0원 적립 원장은 남기지 않는다")
        assertEquals(0L, pointBalance(buyerId))
    }

    @Test
    fun `두 번 확정하면 409와 50007이고 적립금은 한 번만 지급된다`() {
        val fixture = seedOrder()

        mockMvc.perform(confirmRequest(token, fixture.orderPublicId)).andExpect(status().isNoContent)
        mockMvc
            .perform(confirmRequest(token, fixture.orderPublicId))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.errorCode").value(50007))

        assertEquals(1, rewards(fixture.orderPublicId).size, "재호출로 적립이 두 번 되면 안 된다")
        assertEquals(97L, pointBalance(buyerId))
    }

    @Test
    fun `배송완료가 아닌 주문은 409와 50006으로 거절한다`() {
        val shipping = seedOrder(status = OrderStatus.SHIPPING)

        mockMvc
            .perform(confirmRequest(token, shipping.orderPublicId))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.errorCode").value(50006))

        assertEquals(OrderStatus.SHIPPING, findOrder(shipping.orderPublicId).status)
        assertEquals(emptyList<Reward>(), rewards(shipping.orderPublicId))
        assertEquals(emptyList<Triple<OrderStatus, OrderStatus, TriggeredBy>>(), histories(shipping.orderPublicId))
    }

    @Test
    fun `결제완료 주문도 409와 50006으로 거절한다`() {
        val paid = seedOrder(status = OrderStatus.PAID)

        mockMvc
            .perform(confirmRequest(token, paid.orderPublicId))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.errorCode").value(50006))
    }

    @Test
    fun `진행 중 클레임이 있으면 409와 50006으로 거절하고 상태를 그대로 둔다`() {
        val fixture = seedOrder()
        seedClaim(fixture, ClaimStatus.CLAIM_REQUESTED)

        mockMvc
            .perform(confirmRequest(token, fixture.orderPublicId))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.errorCode").value(50006))

        // 확정은 비가역이라 클레임 결론 전에 확정되면 되돌릴 경로가 없다 — 선점 UPDATE까지 롤백돼야 한다
        assertEquals(OrderStatus.DELIVERED, findOrder(fixture.orderPublicId).status)
        assertNull(findOrder(fixture.orderPublicId).purchaseConfirmedAt)
        assertEquals(emptyList<Reward>(), rewards(fixture.orderPublicId))
        assertEquals(emptyList<Triple<OrderStatus, OrderStatus, TriggeredBy>>(), histories(fixture.orderPublicId))
    }

    @Test
    fun `수거 중 클레임도 확정을 막는다`() {
        val fixture = seedOrder()
        seedClaim(fixture, ClaimStatus.COLLECTING)

        mockMvc
            .perform(confirmRequest(token, fixture.orderPublicId))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.errorCode").value(50006))
    }

    @Test
    fun `거부된 클레임은 확정을 막지 않는다`() {
        val fixture = seedOrder()
        seedClaim(fixture, ClaimStatus.REJECTED)

        mockMvc.perform(confirmRequest(token, fixture.orderPublicId)).andExpect(status().isNoContent)

        assertEquals(OrderStatus.PURCHASE_CONFIRMED, findOrder(fixture.orderPublicId).status)
    }

    @Test
    fun `타인 주문은 존재를 숨겨 404와 50000으로 거절한다`() {
        val strangerId = createActiveUser()
        val fixture = seedOrder(buyer = strangerId)

        mockMvc
            .perform(confirmRequest(token, fixture.orderPublicId))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.errorCode").value(50000))

        assertEquals(OrderStatus.DELIVERED, findOrder(fixture.orderPublicId).status)
        assertEquals(emptyList<Reward>(), rewards(fixture.orderPublicId))
    }

    @Test
    fun `없는 주문은 404와 50000으로 거절한다`() {
        mockMvc
            .perform(confirmRequest(token, "01J0000000000000000000NONE"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.errorCode").value(50000))
    }

    @Test
    fun `토큰이 없으면 401을 준다`() {
        val fixture = seedOrder()

        mockMvc
            .perform(post("$PATH_PREFIX/${fixture.orderPublicId}/confirm"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.errorCode").value(20004))
    }

    @Test
    fun `확정한 품목은 미작성 리뷰 목록에 나온다`() {
        val fixture = seedOrder()

        assertEquals(emptyList<Long>(), unreviewedOrderItemIds(), "확정 전에는 리뷰 대기 목록이 비어 있어야 한다")

        mockMvc.perform(confirmRequest(token, fixture.orderPublicId)).andExpect(status().isNoContent)

        assertEquals(listOf(fixture.orderItemId), unreviewedOrderItemIds(), "확정하면 리뷰 작성 대기 목록이 열려야 한다")
    }

    @Test
    fun `동시에 확정하면 하나만 204이고 적립 원장도 1행이다`() {
        val fixture = seedOrder()
        transitionBarrier.enable()

        val results = raceTwo { confirmRequest(token, fixture.orderPublicId) }

        assertEquals(listOf(204, 409), results.map { it.first }.sorted(), "하나만 확정돼야 한다")
        assertEquals(
            50007,
            JsonPath.read<Int>(results.first { it.first == 409 }.second, "$.errorCode"),
            "배송완료에서 나가는 전이는 구매확정뿐이라 경합의 패자는 이미 확정된 것이다",
        )
        assertEquals(1, rewards(fixture.orderPublicId).size, "적립이 두 번 되면 안 된다")
        assertEquals(97L, pointBalance(buyerId))
        assertEquals(1, histories(fixture.orderPublicId).size, "이력도 1행이어야 한다")
    }

    private fun confirmRequest(
        accessToken: String,
        orderPublicId: String,
    ): MockHttpServletRequestBuilder =
        post("$PATH_PREFIX/$orderPublicId/confirm")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")

    private fun unreviewedOrderItemIds(): List<Long> {
        val body =
            mockMvc
                .perform(
                    get("/api/v1/users/me/reviews")
                        .param("tab", "unreviewed")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer $token"),
                ).andExpect(status().isOk)
                .andReturn()
                .response
                .getContentAsString(Charsets.UTF_8)
        return JsonPath.read<List<Int>>(body, "$.data.items[*].orderItemId").map { it.toLong() }
    }

    private fun findOrder(orderPublicId: String): Order =
        tx.execute {
            em
                .createQuery("select o from Order o where o.publicId = :publicId", Order::class.java)
                .setParameter("publicId", orderPublicId)
                .singleResult
        }!!

    private fun histories(orderPublicId: String): List<Triple<OrderStatus, OrderStatus, TriggeredBy>> =
        tx.execute {
            em
                .createQuery(
                    "select h from OrderStatusHistory h where h.order.publicId = :publicId order by h.id",
                    com.aechak.domain.order.order.OrderStatusHistory::class.java,
                ).setParameter("publicId", orderPublicId)
                .resultList
                .map { Triple(it.fromStatus!!, it.toStatus, it.triggeredBy) }
        }!!

    private fun rewards(orderPublicId: String): List<Reward> =
        tx.execute {
            em
                .createQuery(
                    "select t from PointTransaction t where t.idempotencyKey = :key",
                    com.aechak.domain.user.point.PointTransaction::class.java,
                ).setParameter("key", "EARN:PURCHASE:$orderPublicId")
                .resultList
                .map { Reward(it.amount, it.transactionType, it.sourceType, it.sourceId) }
        }!!

    private fun pointBalance(userId: Long): Long = tx.execute { em.find(User::class.java, userId).pointBalance }!!

    private fun userUpdatedAt(userId: Long): LocalDateTime = tx.execute { em.find(User::class.java, userId).updatedAt }!!

    private fun seedClaim(
        fixture: Fixture,
        status: ClaimStatus,
    ) = tx.execute {
        val order = em.find(Order::class.java, fixture.orderId)
        val claim =
            Claim.request(
                claimType = ClaimType.RETURN,
                order = order,
                items = listOf(ClaimItem.of(order.items.first(), quantity = 1, refundAmount = 10_000L)),
            )
        em.persist(claim)
        em.flush()
        // 요청 상태 외의 상태는 전이 API가 아직 없어 쿼리로 심는다 — 전이 규칙 자체는 이 테스트의 대상이 아니다
        em
            .createQuery("update Claim c set c.status = :status where c.id = :id")
            .setParameter("status", status)
            .setParameter("id", claim.id)
            .executeUpdate()
    }

    private fun seedOrder(
        buyer: Long = buyerId,
        status: OrderStatus = OrderStatus.DELIVERED,
        unitPrice: Long = 10_000L,
        quantity: Int = 2,
        discountAllocatedAmount: Long = 500L,
    ): Fixture =
        tx.execute {
            val tag = "s${sig++}"
            val sellerUserId = ensureSeller()
            val category = Category.create(null, 1, "카테고리-$tag", null, 1)
            em.persist(category)
            val product =
                Product.register(category, sellerUserId, "사료-$tag", null, "products/$tag.jpg", unitPrice, null, null, null)
            em.persist(product)
            em.flush()
            val version =
                ProductVersion.snapshot(
                    product = product,
                    versionNo = 1,
                    nameSnapshot = "사료-$tag",
                    priceSnapshot = unitPrice,
                    statusSnapshot = SaleStatus.ON_SALE,
                    thumbnailKeySnapshot = "products/$tag.jpg",
                    changedBy = VersionChangedBy.SELLER,
                )
            em.persist(version)
            val combination = OptionCombination.create(product, "블랙 / L", 0L, 100, "sig-$tag")
            em.persist(combination)
            em.flush()

            val group =
                OrderGroup.create(
                    buyerId = buyer,
                    deliveryAddressId = 0L,
                    deliveryAddress = deliveryAddressSnapshot(),
                    usedPoint = 0L,
                    totalProductAmount = unitPrice * quantity,
                    totalShippingFee = 3_000L,
                    idempotencyKey = "idem-$tag-$buyer",
                    expiresAt = LocalDateTime.now().plusMinutes(10),
                )
            em.persist(group)
            val item =
                OrderItem.of(
                    productId = product.id,
                    optionCombinationId = combination.id,
                    optionNameSnapshot = combination.name,
                    quantity = quantity,
                    unitPriceSnapshot = unitPrice,
                    discountAllocatedAmount = discountAllocatedAmount,
                    productVersionId = version.id,
                )
            val order =
                Order.create(
                    orderGroup = group,
                    sellerId = sellerUserId,
                    sellerNameSnapshot = "store-$sellerUserId",
                    allocatedCouponDiscount = 0L,
                    sellerShippingFee = 3_000L,
                    baseShippingFeeSnapshot = null,
                    freeShippingThresholdSnapshot = null,
                    items = listOf(item),
                )
            em.persist(order)
            em.flush()
            em
                .createQuery("update Order o set o.status = :status where o.id = :id")
                .setParameter("status", status)
                .setParameter("id", order.id)
                .executeUpdate()
            Fixture(orderPublicId = order.publicId, orderId = order.id, orderItemId = item.id)
        }!!

    /** 셀러 1명으로 충분하다 — 주문의 sellerId가 가리키는 행만 있으면 된다. */
    private fun ensureSeller(): Long {
        if (em.find(Seller::class.java, SELLER_USER_ID) == null) {
            em.persist(Seller.open(userId = SELLER_USER_ID, storeName = "store-$SELLER_USER_ID", baseShippingFee = 3_000L))
            em.flush()
        }
        return SELLER_USER_ID
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

    private fun raceTwo(request: () -> MockHttpServletRequestBuilder): List<Pair<Int, String>> {
        val pool = Executors.newFixedThreadPool(2)
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        try {
            val futures =
                listOf(request(), request()).map { builder ->
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

    private data class Fixture(
        val orderPublicId: String,
        val orderId: Long,
        val orderItemId: Long,
    )

    private data class Reward(
        val amount: Long,
        val transactionType: com.aechak.domain.user.point.enums.PointTransactionType,
        val sourceType: String?,
        val sourceId: Long?,
    )

    /** 두 요청이 주문 조회를 끝낸 지점에서 만나게 하는 장치. enable한 테스트에서만 동작한다. */
    class TransitionBarrier {
        private val barrier = CyclicBarrier(2)
        private val arrivals = AtomicInteger()

        @Volatile
        private var enabled = false

        /** 싱글턴이라 테스트 간에 도착 수가 남는다 — 켤 때마다 되감는다. */
        fun enable() {
            arrivals.set(0)
            barrier.reset()
            enabled = true
        }

        fun disable() {
            enabled = false
        }

        fun awaitBothArrived() {
            if (enabled && arrivals.getAndIncrement() < BOTH) {
                barrier.await(AWAIT_SECONDS, TimeUnit.SECONDS)
            }
        }

        private companion object {
            private const val BOTH = 2
            private const val AWAIT_SECONDS = 10L
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    class TransitionRaceConfig {
        @Bean
        fun transitionBarrier(): TransitionBarrier = TransitionBarrier()

        @Bean
        @Primary
        fun barrieredOrderRepository(
            delegate: OrderRepositoryAdapter,
            barrier: TransitionBarrier,
        ): OrderRepository =
            object : OrderRepository by delegate {
                override fun transitionStatusIf(
                    id: Long,
                    expected: OrderStatus,
                    next: OrderStatus,
                ): Boolean {
                    barrier.awaitBothArrived()
                    return delegate.transitionStatusIf(id, expected, next)
                }
            }
    }

    private companion object {
        private const val PATH_PREFIX = "/api/v1/orders"
        private const val SELLER_USER_ID = 77L
    }
}
