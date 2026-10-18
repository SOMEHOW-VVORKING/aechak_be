package com.aechak.seller.order

import com.aechak.api.support.IntegrationTestBase
import com.aechak.application.order.seller.usecase.SellerOrderUseCase
import com.aechak.application.order.seller.usecase.command.RegisterShipmentCommand
import com.aechak.domain.order.group.DeliveryAddressSnapshot
import com.aechak.domain.order.group.OrderGroup
import com.aechak.domain.order.order.Order
import com.aechak.domain.order.order.OrderItem
import com.aechak.domain.order.order.OrderStatusHistory
import com.aechak.domain.order.order.enums.OrderStatus
import com.aechak.domain.order.order.enums.TriggeredBy
import com.aechak.domain.order.shipment.CourierMaster
import com.aechak.domain.order.shipment.Shipment
import com.aechak.domain.order.shipment.enums.ShipmentType
import com.aechak.domain.seller.seller.Seller
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
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
import java.time.LocalDateTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * 셀러 송장 등록 통합. 상품 준비중에서 등록하면 발송 대기를 거쳐 2단 전이하고 이력도 2행이다.
 * 전이의 심판이 조건부 UPDATE라 Testcontainers 실 MySQL이 전제다(H2 금지).
 */
class SellerShipmentIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var context: WebApplicationContext

    @Autowired
    private lateinit var securityFilterChain: FilterChainProxy

    @Autowired
    private lateinit var sellerOrderUseCase: SellerOrderUseCase

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
        seedCourier("CJ")
    }

    @Test
    fun `상품 준비중 주문에 송장을 등록하면 201과 배송중이 된다`() {
        val orderPublicId = seedOrder(OrderStatus.PREPARING)

        mockMvc
            .perform(shipmentRequest(token, orderPublicId))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.data.shipmentId").exists())
            .andExpect(jsonPath("$.data.dispatchedAt").exists())
            .andExpect(jsonPath("$.data.orderStatus").value("SHIPPING"))

        assertEquals(OrderStatus.SHIPPING, statusOf(orderPublicId))
    }

    @Test
    fun `송장 등록은 발송 1건을 남긴다`() {
        val orderPublicId = seedOrder(OrderStatus.PREPARING)

        mockMvc.perform(shipmentRequest(token, orderPublicId)).andExpect(status().isCreated)

        val shipments = shipmentsOf(orderPublicId)
        assertEquals(1, shipments.size)
        val shipment = shipments.first()
        assertEquals("123456789012", shipment.trackingNumber)
        assertEquals("CJ", shipment.courier.courierCode)
        assertEquals(ShipmentType.INITIAL, shipment.shipmentType)
        assertNotNull(shipment.dispatchedAt, "발송 시각이 찍혀야 한다")
    }

    @Test
    fun `상품 준비중에서 등록하면 발송 대기를 거쳐 이력 2행을 남긴다`() {
        val orderPublicId = seedOrder(OrderStatus.PREPARING)

        mockMvc.perform(shipmentRequest(token, orderPublicId)).andExpect(status().isCreated)

        val histories = historiesOf(orderPublicId)
        assertEquals(2, histories.size, "2단 전이는 이력 2행이어야 한다")
        assertEquals(OrderStatus.PREPARING, histories[0].fromStatus)
        assertEquals(OrderStatus.DISPATCH_PENDING, histories[0].toStatus)
        assertEquals(OrderStatus.DISPATCH_PENDING, histories[1].fromStatus)
        assertEquals(OrderStatus.SHIPPING, histories[1].toStatus)
        assertTrue(histories.all { it.triggeredBy == TriggeredBy.SELLER })
    }

    @Test
    fun `발송 대기 주문에 송장을 등록하면 201과 이력 1행이다`() {
        val orderPublicId = seedOrder(OrderStatus.DISPATCH_PENDING)

        mockMvc
            .perform(shipmentRequest(token, orderPublicId))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.data.orderStatus").value("SHIPPING"))

        assertEquals(OrderStatus.SHIPPING, statusOf(orderPublicId))
        val histories = historiesOf(orderPublicId)
        assertEquals(1, histories.size)
        assertEquals(OrderStatus.DISPATCH_PENDING, histories[0].fromStatus)
        assertEquals(OrderStatus.SHIPPING, histories[0].toStatus)
    }

    @Test
    fun `발송과 이력은 전이와 같은 트랜잭션에 들어간다`() {
        val orderPublicId = seedOrder(OrderStatus.DISPATCH_PENDING)

        tx.execute { status ->
            sellerOrderUseCase.registerShipment(
                RegisterShipmentCommand(
                    sellerId = sellerUserId,
                    orderPublicId = orderPublicId,
                    courierCode = "CJ",
                    trackingNumber = "123456789012",
                ),
            )
            em.flush()
            em.clear()
            // 커밋 전에 셋이 함께 보여야 한다 — 발송이나 이력이 커밋 뒤에 저장되면 여기서 0건이다
            assertEquals(OrderStatus.SHIPPING, statusOf(orderPublicId))
            assertEquals(1, shipmentCount(orderPublicId))
            assertEquals(1, historiesOf(orderPublicId).size)
            status.setRollbackOnly()
        }

        assertEquals(OrderStatus.DISPATCH_PENDING, statusOf(orderPublicId))
        assertEquals(0, shipmentCount(orderPublicId), "전이가 되돌려졌는데 발송이 남으면 안 된다")
        assertEquals(0, historiesOf(orderPublicId).size)
    }

    @Test
    fun `같은 주문에 송장을 두 번 등록하면 409와 50302다`() {
        val orderPublicId = seedOrder(OrderStatus.PREPARING)
        mockMvc.perform(shipmentRequest(token, orderPublicId)).andExpect(status().isCreated)

        mockMvc
            .perform(shipmentRequest(token, orderPublicId, trackingNumber = "999999999999"))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.errorCode").value(50302))

        assertEquals(1, shipmentCount(orderPublicId), "거절된 요청이 발송을 남기면 안 된다")
        assertEquals("123456789012", shipmentsOf(orderPublicId).first().trackingNumber)
    }

    @Test
    fun `없는 택배사 코드는 404와 50303이다`() {
        val orderPublicId = seedOrder(OrderStatus.PREPARING)

        mockMvc
            .perform(shipmentRequest(token, orderPublicId, courierCode = "NOPE"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.errorCode").value(50303))

        assertEquals(OrderStatus.PREPARING, statusOf(orderPublicId))
        assertEquals(0, shipmentCount(orderPublicId))
    }

    @Test
    fun `비활성 택배사 코드도 404와 50303이다`() {
        val orderPublicId = seedOrder(OrderStatus.PREPARING)
        seedCourier("LOTTE", isActive = false)

        mockMvc
            .perform(shipmentRequest(token, orderPublicId, courierCode = "LOTTE"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.errorCode").value(50303))
    }

    @Test
    fun `택배사 패턴에 어긋나는 송장번호는 400과 50304다`() {
        val orderPublicId = seedOrder(OrderStatus.PREPARING)
        seedCourier("HANJIN", trackingFormatPattern = "\\d{12}")

        mockMvc
            .perform(shipmentRequest(token, orderPublicId, courierCode = "HANJIN", trackingNumber = "12345"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(50304))

        assertEquals(OrderStatus.PREPARING, statusOf(orderPublicId))
        assertEquals(0, shipmentCount(orderPublicId))
    }

    @Test
    fun `택배사 패턴에 맞는 송장번호는 201이다`() {
        val orderPublicId = seedOrder(OrderStatus.PREPARING)
        seedCourier("HANJIN", trackingFormatPattern = "\\d{12}")

        mockMvc
            .perform(shipmentRequest(token, orderPublicId, courierCode = "HANJIN"))
            .andExpect(status().isCreated)
    }

    @Test
    fun `패턴이 없는 택배사는 송장번호를 검증하지 않는다`() {
        val orderPublicId = seedOrder(OrderStatus.PREPARING)

        mockMvc
            .perform(shipmentRequest(token, orderPublicId, trackingNumber = "아무값1"))
            .andExpect(status().isCreated)

        assertEquals("아무값1", shipmentsOf(orderPublicId).first().trackingNumber)
    }

    @Test
    fun `배송중 주문의 송장 등록은 409와 50005다`() {
        val orderPublicId = seedOrder(OrderStatus.SHIPPING)

        mockMvc
            .perform(shipmentRequest(token, orderPublicId))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.errorCode").value(50005))

        assertEquals(0, shipmentCount(orderPublicId))
    }

    @Test
    fun `결제완료 주문의 송장 등록은 409와 50005다`() {
        val orderPublicId = seedOrder(OrderStatus.PAID)

        mockMvc
            .perform(shipmentRequest(token, orderPublicId))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.errorCode").value(50005))

        assertEquals(OrderStatus.PAID, statusOf(orderPublicId))
    }

    @Test
    fun `타 셀러 주문의 송장 등록은 404와 50000이다`() {
        val orderPublicId = seedOrder(OrderStatus.PREPARING)
        val otherSeller = createActiveUser()
        openSeller(otherSeller)

        mockMvc
            .perform(shipmentRequest(mintAccessToken(otherSeller), orderPublicId))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.errorCode").value(50000))

        assertEquals(OrderStatus.PREPARING, statusOf(orderPublicId), "거절된 요청이 상태를 바꾸면 안 된다")
        assertEquals(0, shipmentCount(orderPublicId))
    }

    @Test
    fun `없는 주문의 송장 등록은 404와 50000이다`() {
        seedOrder(OrderStatus.PREPARING)

        mockMvc
            .perform(shipmentRequest(token, "01JZZZZZZZZZZZZZZZZZZZZZZZ"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.errorCode").value(50000))
    }

    @Test
    fun `셀러가 아닌 계정의 송장 등록은 403과 50003이다`() {
        val orderPublicId = seedOrder(OrderStatus.PREPARING)
        val notSeller = createActiveUser()

        mockMvc
            .perform(shipmentRequest(mintAccessToken(notSeller), orderPublicId))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.errorCode").value(50003))
    }

    @Test
    fun `미인증 송장 등록은 401과 20004다`() {
        val orderPublicId = seedOrder(OrderStatus.PREPARING)

        mockMvc
            .perform(
                post("/api/v1/sellers/me/orders/$orderPublicId/shipment")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body("CJ", "123456789012")),
            ).andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.errorCode").value(20004))
    }

    @Test
    fun `택배사 코드나 송장번호가 공백이면 90001이다`() {
        val orderPublicId = seedOrder(OrderStatus.PREPARING)

        mockMvc
            .perform(shipmentRequest(token, orderPublicId, courierCode = " "))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(90001))
        mockMvc
            .perform(shipmentRequest(token, orderPublicId, trackingNumber = ""))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(90001))

        assertEquals(0, shipmentCount(orderPublicId))
    }

    /** 두 트랜잭션이 같은 상태를 읽는 지점까지는 HTTP 경계에서 맞출 수 없어, 겹친 요청의 결과만 고정한다. */
    @Test
    fun `동시 송장 등록은 하나만 성공한다`() {
        val orderPublicId = seedOrder(OrderStatus.PREPARING)

        val results = raceTwo { shipmentRequest(token, orderPublicId) }

        assertEquals(listOf(201, 409), results.map { it.first }.sorted(), "결과: $results")
        val loser = results.first { it.first == 409 }.second
        // 발송이 먼저 보이면 50302, 전이 선점에서 지면 50005 — 둘 중 어느 쪽이든 발송은 1건이어야 한다
        assertTrue(errorCodeOf(loser) in setOf("50302", "50005"), "경합에서 진 쪽의 코드: $loser")
        assertEquals(OrderStatus.SHIPPING, statusOf(orderPublicId))
        assertEquals(1, shipmentCount(orderPublicId), "발송이 두 건 생기면 안 된다")
        assertEquals(2, historiesOf(orderPublicId).size, "성공한 2단 전이만 이력을 남겨야 한다")
    }

    // ---------- 헬퍼 ----------

    private fun body(
        courierCode: String,
        trackingNumber: String,
    ): String = """{"courierCode": "$courierCode", "trackingNumber": "$trackingNumber"}"""

    private fun shipmentRequest(
        token: String,
        orderPublicId: String,
        courierCode: String = "CJ",
        trackingNumber: String = "123456789012",
    ): MockHttpServletRequestBuilder =
        post("/api/v1/sellers/me/orders/$orderPublicId/shipment")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body(courierCode, trackingNumber))

    private fun errorCodeOf(body: String): String? = Regex("\"errorCode\"\\s*:\\s*(\\d+)").find(body)?.groupValues?.get(1)

    private fun seedCourier(
        courierCode: String,
        isActive: Boolean = true,
        trackingFormatPattern: String? = null,
    ) {
        tx.execute {
            em.persist(
                CourierMaster.register(
                    courierCode = courierCode,
                    courierName = "택배사$courierCode",
                    isActive = isActive,
                    trackingFormatPattern = trackingFormatPattern,
                ),
            )
        }
    }

    private fun openSeller(userId: Long) {
        tx.execute { em.persist(Seller.open(userId, "애착상회$userId", 0L)) }
    }

    /** 주문그룹 1건 + 셀러 주문 1건을 심고 주문 공개 식별자를 돌려준다. */
    private fun seedOrder(status: OrderStatus): String =
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
                    idempotencyKey = "shipment-${status.name}-${System.nanoTime()}",
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

    private fun statusOf(orderPublicId: String): OrderStatus =
        tx.execute {
            em
                .createQuery("select o.status from Order o where o.publicId = :publicId", OrderStatus::class.java)
                .setParameter("publicId", orderPublicId)
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

    private fun shipmentsOf(orderPublicId: String): List<Shipment> =
        tx.execute {
            em
                .createQuery(
                    "select s from Shipment s where s.order.publicId = :publicId order by s.id asc",
                    Shipment::class.java,
                ).setParameter("publicId", orderPublicId)
                .resultList
        }!!

    private fun shipmentCount(orderPublicId: String): Int =
        tx.execute {
            em
                .createQuery(
                    "select count(s) from Shipment s where s.order.publicId = :publicId",
                    Long::class.javaObjectType,
                ).setParameter("publicId", orderPublicId)
                .singleResult
                .toInt()
        }!!

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
