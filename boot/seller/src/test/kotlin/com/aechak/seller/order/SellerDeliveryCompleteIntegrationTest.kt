package com.aechak.seller.order

import com.aechak.api.support.IntegrationTestBase
import com.aechak.application.order.seller.usecase.SellerOrderUseCase
import com.aechak.application.order.seller.usecase.command.CompleteDeliveryCommand
import com.aechak.domain.order.group.DeliveryAddressSnapshot
import com.aechak.domain.order.group.OrderGroup
import com.aechak.domain.order.order.Order
import com.aechak.domain.order.order.OrderItem
import com.aechak.domain.order.order.OrderStatusHistory
import com.aechak.domain.order.order.enums.OrderStatus
import com.aechak.domain.order.order.enums.TriggeredBy
import com.aechak.domain.order.order.repository.OrderRepository
import com.aechak.domain.order.shipment.CourierMaster
import com.aechak.domain.order.shipment.Shipment
import com.aechak.domain.order.shipment.enums.ShipmentType
import com.aechak.domain.seller.seller.Seller
import com.aechak.infra.persistence.order.order.OrderRepositoryAdapter
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
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
 * 셀러 배송완료 전이 통합. 배송완료 시각에 구매확정 기한이 함께 박혀야 다음 단계의 자동확정이 동작한다.
 * 전이의 심판이 조건부 UPDATE라 Testcontainers 실 MySQL이 전제다(H2 금지).
 */
@Import(SellerDeliveryCompleteIntegrationTest.DeliveryRaceConfig::class)
class SellerDeliveryCompleteIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var context: WebApplicationContext

    @Autowired
    private lateinit var securityFilterChain: FilterChainProxy

    @Autowired
    private lateinit var sellerOrderUseCase: SellerOrderUseCase

    @Autowired
    private lateinit var deliveryBarrier: DeliveryBarrier

    private lateinit var mockMvc: MockMvc
    private var sellerUserId = 0L
    private lateinit var token: String

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
        seedCourier()
    }

    @AfterEach
    fun resetBarrier() {
        deliveryBarrier.disable()
    }

    @Test
    fun `배송중 주문을 배송완료하면 204와 배송완료가 된다`() {
        val orderPublicId = seedShippingOrder()

        mockMvc
            .perform(deliveryCompleteRequest(token, orderPublicId))
            .andExpect(status().isNoContent)

        assertEquals(OrderStatus.DELIVERED, statusOf(orderPublicId))
        assertNotNull(shipmentOf(orderPublicId).deliveredAt, "배송완료 시각이 찍혀야 한다")
    }

    @Test
    fun `배송완료는 배송완료 시각에 기본 5일을 더해 구매확정 기한을 부여한다`() {
        val orderPublicId = seedShippingOrder()

        mockMvc.perform(deliveryCompleteRequest(token, orderPublicId)).andExpect(status().isNoContent)

        val deliveredAt = requireNotNull(shipmentOf(orderPublicId).deliveredAt)
        assertEquals(
            deliveredAt.plusDays(5),
            autoConfirmDueAtOf(orderPublicId),
            "구매확정 기한이 비거나 어긋나면 자동확정이 영구히 돌지 않는다",
        )
    }

    @Test
    fun `배송완료는 셀러가 일으킨 이력 1행을 남긴다`() {
        val orderPublicId = seedShippingOrder()

        mockMvc.perform(deliveryCompleteRequest(token, orderPublicId)).andExpect(status().isNoContent)

        val histories = historiesOf(orderPublicId)
        assertEquals(1, histories.size)
        assertEquals(OrderStatus.SHIPPING, histories[0].fromStatus)
        assertEquals(OrderStatus.DELIVERED, histories[0].toStatus)
        assertEquals(TriggeredBy.SELLER, histories[0].triggeredBy)
    }

    @Test
    fun `배송완료와 기한과 이력은 전이와 같은 트랜잭션에 들어간다`() {
        val orderPublicId = seedShippingOrder()

        tx.execute { status ->
            sellerOrderUseCase.completeDelivery(
                CompleteDeliveryCommand(sellerId = sellerUserId, orderPublicId = orderPublicId),
            )
            em.flush()
            em.clear()
            // 커밋 전에 셋이 함께 보여야 한다 — 기한이나 이력이 커밋 뒤에 저장되면 여기서 비어 있다
            assertEquals(OrderStatus.DELIVERED, statusOf(orderPublicId))
            assertNotNull(autoConfirmDueAtOf(orderPublicId))
            assertEquals(1, historiesOf(orderPublicId).size)
            status.setRollbackOnly()
        }

        assertEquals(OrderStatus.SHIPPING, statusOf(orderPublicId))
        assertNull(autoConfirmDueAtOf(orderPublicId), "전이가 되돌려졌는데 기한이 남으면 안 된다")
        assertNull(shipmentOf(orderPublicId).deliveredAt)
        assertEquals(0, historiesOf(orderPublicId).size)
    }

    @Test
    fun `이미 배송완료된 주문의 배송완료는 409와 50301이다`() {
        val orderPublicId = seedShippingOrder()
        mockMvc.perform(deliveryCompleteRequest(token, orderPublicId)).andExpect(status().isNoContent)
        val firstDueAt = autoConfirmDueAtOf(orderPublicId)

        mockMvc
            .perform(deliveryCompleteRequest(token, orderPublicId))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.errorCode").value(50301))

        assertEquals(firstDueAt, autoConfirmDueAtOf(orderPublicId), "거절된 요청이 기한을 다시 쓰면 안 된다")
        assertEquals(1, historiesOf(orderPublicId).size)
    }

    @Test
    fun `송장이 없는 배송중 주문의 배송완료는 404와 50300이다`() {
        val orderPublicId = seedOrder(OrderStatus.SHIPPING, withShipment = false)

        mockMvc
            .perform(deliveryCompleteRequest(token, orderPublicId))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.errorCode").value(50300))

        assertEquals(OrderStatus.SHIPPING, statusOf(orderPublicId))
    }

    @Test
    fun `발송 전 주문의 배송완료는 404와 50300이다`() {
        val orderPublicId = seedOrder(OrderStatus.PREPARING, withShipment = false)

        mockMvc
            .perform(deliveryCompleteRequest(token, orderPublicId))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.errorCode").value(50300))

        assertEquals(OrderStatus.PREPARING, statusOf(orderPublicId))
    }

    @Test
    fun `교환 재발송만 있는 주문의 배송완료는 404와 50300이다`() {
        val orderPublicId = seedOrder(OrderStatus.SHIPPING, withShipment = false)
        seedShipment(orderPublicId, ShipmentType.EXCHANGE_RESHIP)

        mockMvc
            .perform(deliveryCompleteRequest(token, orderPublicId))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.errorCode").value(50300))
    }

    @Test
    fun `타 셀러 주문의 배송완료는 404와 50000이다`() {
        val orderPublicId = seedShippingOrder()
        val otherSeller = createActiveUser()
        openSeller(otherSeller)

        mockMvc
            .perform(deliveryCompleteRequest(mintAccessToken(otherSeller), orderPublicId))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.errorCode").value(50000))

        assertEquals(OrderStatus.SHIPPING, statusOf(orderPublicId), "거절된 요청이 상태를 바꾸면 안 된다")
        assertNull(autoConfirmDueAtOf(orderPublicId))
        assertNull(shipmentOf(orderPublicId).deliveredAt)
    }

    @Test
    fun `없는 주문의 배송완료는 404와 50000이다`() {
        seedShippingOrder()

        mockMvc
            .perform(deliveryCompleteRequest(token, "01JZZZZZZZZZZZZZZZZZZZZZZZ"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.errorCode").value(50000))
    }

    @Test
    fun `셀러가 아닌 계정의 배송완료는 403과 50003이다`() {
        val orderPublicId = seedShippingOrder()
        val notSeller = createActiveUser()

        mockMvc
            .perform(deliveryCompleteRequest(mintAccessToken(notSeller), orderPublicId))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.errorCode").value(50003))
    }

    @Test
    fun `미인증 배송완료는 401과 20004다`() {
        val orderPublicId = seedShippingOrder()

        mockMvc
            .perform(post("/api/v1/sellers/me/orders/$orderPublicId/delivery-complete"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.errorCode").value(20004))
    }

    /**
     * 두 요청이 발송 조회를 끝낸 뒤에야 조건부 UPDATE로 들어가게 묶는다 —
     * 이 장치가 없으면 앞 요청이 커밋한 뒤 뒤 요청이 조회하는 순서로도 통과해 선점 UPDATE 제거를 못 잡는다.
     */
    @Test
    fun `동시 배송완료는 하나만 성공한다`() {
        val orderPublicId = seedShippingOrder()
        deliveryBarrier.enable()

        val results = raceTwo { deliveryCompleteRequest(token, orderPublicId) }

        assertEquals(listOf(204, 409), results.map { it.first }.sorted(), "결과: $results")
        val loser = results.first { it.first == 409 }.second
        assertEquals("50301", errorCodeOf(loser), "전이 선점에서 진 쪽은 50301이어야 한다: $loser")
        assertEquals(OrderStatus.DELIVERED, statusOf(orderPublicId))
        assertEquals(1, historiesOf(orderPublicId).size, "성공한 전이만 이력을 남겨야 한다")
    }

    // ---------- 헬퍼 ----------

    private fun deliveryCompleteRequest(
        token: String,
        orderPublicId: String,
    ): MockHttpServletRequestBuilder =
        post("/api/v1/sellers/me/orders/$orderPublicId/delivery-complete")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $token")

    private fun errorCodeOf(body: String): String? = Regex("\"errorCode\"\\s*:\\s*(\\d+)").find(body)?.groupValues?.get(1)

    private fun seedCourier() {
        tx.execute { em.persist(CourierMaster.register(courierCode = "CJ", courierName = "씨제이대한통운")) }
    }

    private fun openSeller(userId: Long) {
        tx.execute { em.persist(Seller.open(userId, "애착상회$userId", 0L)) }
    }

    private fun seedShippingOrder(): String = seedOrder(OrderStatus.SHIPPING, withShipment = true)

    /** 주문그룹 1건 + 셀러 주문 1건(필요하면 최초 발송까지)을 심고 주문 공개 식별자를 돌려준다. */
    private fun seedOrder(
        status: OrderStatus,
        withShipment: Boolean,
    ): String {
        val orderPublicId =
            tx.execute {
                val group =
                    OrderGroup.create(
                        buyerId = 9_999L,
                        deliveryAddressId = 0L,
                        deliveryAddress =
                            DeliveryAddressSnapshot(
                                receiverNameEnc = "enc-receiver",
                                contactNumberEnc = "enc-contact",
                                zipCode = "12345",
                                baseAddress = "서울시 애착구 멍냥로 1",
                                detailAddress = null,
                                deliveryMemo = null,
                            ),
                        usedPoint = 0L,
                        totalProductAmount = 25_000L,
                        totalShippingFee = 0L,
                        idempotencyKey = "delivery-${status.name}-${System.nanoTime()}",
                        expiresAt = LocalDateTime.now().plusMinutes(10),
                    )
                em.persist(group)
                val order =
                    Order.create(
                        orderGroup = group,
                        sellerId = sellerUserId,
                        sellerNameSnapshot = "애착상회$sellerUserId",
                        allocatedCouponDiscount = 0L,
                        sellerShippingFee = 0L,
                        baseShippingFeeSnapshot = null,
                        freeShippingThresholdSnapshot = null,
                        items =
                            listOf(
                                OrderItem.of(
                                    productId = 1L,
                                    optionCombinationId = 2L,
                                    optionNameSnapshot = "기본 / 1개",
                                    quantity = 1,
                                    unitPriceSnapshot = 25_000L,
                                    discountAllocatedAmount = 0L,
                                    productVersionId = 3L,
                                ),
                            ),
                    )
                em.persist(order)
                em.flush()
                em
                    .createQuery("update Order o set o.status = :status where o.id = :id")
                    .setParameter("status", status)
                    .setParameter("id", order.id)
                    .executeUpdate()
                order.publicId
            }!!
        if (withShipment) {
            seedShipment(orderPublicId, ShipmentType.INITIAL)
        }
        return orderPublicId
    }

    private fun seedShipment(
        orderPublicId: String,
        shipmentType: ShipmentType,
    ) {
        tx.execute {
            em.persist(
                Shipment.dispatch(
                    courier = em.find(CourierMaster::class.java, "CJ"),
                    order = orderEntityOf(orderPublicId),
                    trackingNumber = "123456789012",
                    shipmentType = shipmentType,
                ),
            )
        }
    }

    private fun orderEntityOf(orderPublicId: String): Order =
        em
            .createQuery("select o from Order o where o.publicId = :publicId", Order::class.java)
            .setParameter("publicId", orderPublicId)
            .singleResult

    private fun statusOf(orderPublicId: String): OrderStatus =
        tx.execute {
            em
                .createQuery("select o.status from Order o where o.publicId = :publicId", OrderStatus::class.java)
                .setParameter("publicId", orderPublicId)
                .singleResult
        }!!

    private fun autoConfirmDueAtOf(orderPublicId: String): LocalDateTime? =
        tx.execute {
            em
                .createQuery("select o.autoConfirmDueAt from Order o where o.publicId = :publicId", LocalDateTime::class.java)
                .setParameter("publicId", orderPublicId)
                .singleResult
        }

    private fun shipmentOf(orderPublicId: String): Shipment =
        tx.execute {
            em
                .createQuery(
                    "select s from Shipment s where s.order.publicId = :publicId and s.shipmentType = :type",
                    Shipment::class.java,
                ).setParameter("publicId", orderPublicId)
                .setParameter("type", ShipmentType.INITIAL)
                .singleResult
        }!!

    private fun historiesOf(orderPublicId: String): List<OrderStatusHistory> =
        tx.execute {
            em
                .createQuery(
                    "select h from OrderStatusHistory h where h.order.publicId = :publicId order by h.id asc",
                    OrderStatusHistory::class.java,
                ).setParameter("publicId", orderPublicId)
                .resultList
        }!!

    /** 두 요청이 발송 조회를 끝낸 지점에서 만나게 하는 장치. enable한 테스트에서만 동작한다. */
    class DeliveryBarrier {
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
    class DeliveryRaceConfig {
        @Bean
        fun deliveryBarrier(): DeliveryBarrier = DeliveryBarrier()

        @Bean
        @Primary
        fun barrieredOrderRepository(
            delegate: OrderRepositoryAdapter,
            barrier: DeliveryBarrier,
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
}
