package com.aechak.seller.order

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.aechak.api.support.IntegrationTestBase
import com.aechak.application.order.seller.service.SellerOrderService
import com.aechak.application.order.seller.usecase.SellerOrderUseCase
import com.aechak.application.order.seller.usecase.command.RegisterShipmentCommand
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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
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
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * 셀러 송장 등록 통합. 상품 준비중에서 등록하면 발송 대기를 거쳐 2단 전이하고 이력도 2행이다.
 * 전이의 심판이 조건부 UPDATE라 Testcontainers 실 MySQL이 전제다(H2 금지).
 */
@Import(SellerShipmentIntegrationTest.TransitionRaceConfig::class)
class SellerShipmentIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var context: WebApplicationContext

    @Autowired
    private lateinit var securityFilterChain: FilterChainProxy

    @Autowired
    private lateinit var sellerOrderUseCase: SellerOrderUseCase

    @Autowired
    private lateinit var transitionBarrier: TransitionBarrier

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

    @AfterEach
    fun resetBarrier() {
        transitionBarrier.disable()
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
    fun `컴파일되지 않는 패턴은 검증을 건너뛰고 경고 로그를 남긴다`() {
        val orderPublicId = seedOrder(OrderStatus.PREPARING)
        seedCourier("HANJIN", trackingFormatPattern = BROKEN_PATTERN)

        val warnings =
            captureServiceWarnings {
                mockMvc
                    .perform(shipmentRequest(token, orderPublicId, courierCode = "HANJIN", trackingNumber = "아무값1"))
                    .andExpect(status().isCreated)
            }

        assertEquals("아무값1", shipmentsOf(orderPublicId).first().trackingNumber)
        assertEquals(1, warnings.size, "경고 1건이어야 한다. 실제: $warnings")
        val warning = warnings.first()
        assertTrue(warning.contains("HANJIN"), "택배사 코드가 로그에 있어야 한다: $warning")
        assertTrue(warning.contains(BROKEN_PATTERN), "문제의 패턴 문자열이 로그에 있어야 한다: $warning")
    }

    @Test
    fun `깨진 패턴에 개행이 섞여 있어도 경고는 한 줄이다`() {
        val orderPublicId = seedOrder(OrderStatus.PREPARING)
        seedCourier("HANJIN", trackingFormatPattern = "$BROKEN_PATTERN\n줄바꿈")

        val warnings =
            captureServiceWarnings {
                mockMvc
                    .perform(shipmentRequest(token, orderPublicId, courierCode = "HANJIN", trackingNumber = "아무값1"))
                    .andExpect(status().isCreated)
            }

        assertEquals(1, warnings.size, "경고 1건이어야 한다. 실제: $warnings")
        val warning = warnings.first()
        assertFalse(warning.contains("\n") || warning.contains("\r"), "경고에 실제 개행이 없어야 한다: $warning")
        assertTrue(warning.contains("\\n줄바꿈"), "개행은 이스케이프해 싣는다: $warning")
    }

    @Test
    fun `정상 패턴과 패턴 없는 택배사는 경고 로그를 남기지 않는다`() {
        val patterned = seedOrder(OrderStatus.PREPARING)
        val unpatterned = seedOrder(OrderStatus.PREPARING)
        seedCourier("HANJIN", trackingFormatPattern = "\\d{12}")

        val warnings =
            captureServiceWarnings {
                mockMvc
                    .perform(shipmentRequest(token, patterned, courierCode = "HANJIN"))
                    .andExpect(status().isCreated)
                mockMvc
                    .perform(shipmentRequest(token, unpatterned))
                    .andExpect(status().isCreated)
            }

        assertEquals(emptyList<String>(), warnings)
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

    @Test
    fun `교환 재발송이 있어도 최초 송장은 등록된다`() {
        val orderPublicId = seedOrder(OrderStatus.PREPARING)
        seedExchangeReshipment(orderPublicId)

        mockMvc.perform(shipmentRequest(token, orderPublicId)).andExpect(status().isCreated)

        val shipments = shipmentsOf(orderPublicId)
        assertEquals(2, shipments.size)
        assertEquals(listOf(ShipmentType.EXCHANGE_RESHIP, ShipmentType.INITIAL), shipments.map { it.shipmentType })
    }

    @Test
    fun `송장번호 앞뒤 공백은 지우고 저장한다`() {
        val orderPublicId = seedOrder(OrderStatus.PREPARING)
        seedCourier("HANJIN", trackingFormatPattern = "\\d{12}")

        mockMvc
            .perform(shipmentRequest(token, orderPublicId, courierCode = " HANJIN ", trackingNumber = " 123456789012 "))
            .andExpect(status().isCreated)

        assertEquals("123456789012", shipmentsOf(orderPublicId).first().trackingNumber)
    }

    @Test
    fun `송장번호가 50자를 넘으면 90001이다`() {
        val orderPublicId = seedOrder(OrderStatus.PREPARING)

        mockMvc
            .perform(shipmentRequest(token, orderPublicId, trackingNumber = "1".repeat(50)))
            .andExpect(status().isCreated)
        mockMvc
            .perform(shipmentRequest(token, seedOrder(OrderStatus.PREPARING), trackingNumber = "1".repeat(51)))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(90001))

        assertEquals(1, shipmentCount(orderPublicId))
    }

    @Test
    fun `택배사 코드가 30자를 넘으면 90001이다`() {
        val orderPublicId = seedOrder(OrderStatus.PREPARING)

        mockMvc
            .perform(shipmentRequest(token, orderPublicId, courierCode = "C".repeat(31)))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(90001))
    }

    /**
     * 두 요청이 주문·중복 조회를 모두 끝낸 뒤에야 첫 조건부 UPDATE로 들어가게 묶는다 —
     * 이 장치가 없으면 앞 요청이 커밋한 뒤 뒤 요청이 조회하는 순서로도 통과해 선점 UPDATE 제거를 못 잡는다.
     */
    @Test
    fun `동시 송장 등록은 하나만 성공한다`() {
        val orderPublicId = seedOrder(OrderStatus.PREPARING)
        transitionBarrier.enable()

        val results = raceTwo { shipmentRequest(token, orderPublicId) }

        assertEquals(listOf(201, 409), results.map { it.first }.sorted(), "결과: $results")
        val loser = results.first { it.first == 409 }.second
        assertEquals("50005", errorCodeOf(loser), "전이 선점에서 진 쪽은 50005여야 한다: $loser")
        assertEquals(OrderStatus.SHIPPING, statusOf(orderPublicId))
        assertEquals(1, shipmentCount(orderPublicId), "발송이 두 건 생기면 안 된다")
        assertEquals(2, historiesOf(orderPublicId).size, "성공한 2단 전이만 이력을 남겨야 한다")
    }

    @Test
    fun `발송 대기 주문의 동시 송장 등록도 하나만 성공한다`() {
        val orderPublicId = seedOrder(OrderStatus.DISPATCH_PENDING)
        transitionBarrier.enable()

        val results = raceTwo { shipmentRequest(token, orderPublicId) }

        assertEquals(listOf(201, 409), results.map { it.first }.sorted(), "결과: $results")
        assertEquals("50005", errorCodeOf(results.first { it.first == 409 }.second))
        assertEquals(1, shipmentCount(orderPublicId))
        assertEquals(1, historiesOf(orderPublicId).size)
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

    /** 송장 등록 중 서비스가 남긴 WARN 메시지를 모은다 — 패턴이 깨졌을 때 침묵하지 않는지 본다. */
    private fun captureServiceWarnings(block: () -> Unit): List<String> {
        val logger = LoggerFactory.getLogger(SellerOrderService::class.java.packageName) as Logger
        val appender = ListAppender<ILoggingEvent>()
        appender.start()
        logger.addAppender(appender)
        try {
            block()
        } finally {
            logger.detachAppender(appender)
            appender.stop()
        }
        return appender.list.filter { it.level == Level.WARN }.map { it.formattedMessage }
    }

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

    /** 같은 주문에 교환 재발송 1건을 심는다 — 최초 발송 중복 판정이 종류를 좁혀 보는지 가른다. */
    private fun seedExchangeReshipment(orderPublicId: String) {
        tx.execute {
            val order =
                em
                    .createQuery("select o from Order o where o.publicId = :publicId", Order::class.java)
                    .setParameter("publicId", orderPublicId)
                    .singleResult
            val courier = em.find(CourierMaster::class.java, "CJ")
            em.persist(
                Shipment.dispatch(
                    courier = courier,
                    order = order,
                    trackingNumber = "888888888888",
                    shipmentType = ShipmentType.EXCHANGE_RESHIP,
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

    /** 두 요청이 조회를 끝낸 지점에서 만나게 하는 장치. enable한 테스트에서만 동작한다. */
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

        /** 첫 전이를 요구한 두 요청만 묶는다 — 승자의 두 번째 전이는 기다리지 않는다. */
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

    private companion object {
        /** 닫히지 않은 중괄호 — 정규식으로 컴파일되지 않는다. */
        private const val BROKEN_PATTERN = "\\d{12"
    }
}
