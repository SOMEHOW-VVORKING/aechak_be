package com.aechak.batch.order

import com.aechak.api.support.DatabaseCleaner
import com.aechak.batch.order.config.OrderDeliveryProperties
import com.aechak.batch.support.BatchIntegrationTestBase
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
import com.aechak.infra.persistence.order.order.OrderRepositoryAdapter
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.support.TransactionTemplate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.ConcurrentHashMap

/** 기본값 5와 다른 값을 설정해야 "설정이 반영되는가"를 가를 수 있다. */
private const val CONFIGURED_AUTO_CONFIRM_DAYS = 7L

/**
 * 집화·배송완료 자동 배치 통합. 실제 택배 추적이 없는 동안 발송 시각만 보고 넘기므로,
 * 대상 선별과 재실행 멱등성이 이 배치의 전부다.
 */
@Import(OrderDeliveryScheduleIntegrationTest.FailingTransitionConfig::class)
@TestPropertySource(
    properties = [
        "order.delivery.enabled=true",
        "order.delivery.cron=0 0 0 29 2 *", // 스케줄이 저절로 돌지 않게 — 테스트가 직접 호출한다
        "order.delivery.scan-limit=2", // 페이지 경계를 실제로 넘게 — 상한에서 끊고 끝내는 회귀를 잡는다
        "order.auto-confirm.days=$CONFIGURED_AUTO_CONFIRM_DAYS", // 기본값(5)과 다른 값 — 설정이 무시되는 회귀를 잡는다
    ],
)
class OrderDeliveryScheduleIntegrationTest : BatchIntegrationTestBase() {
    @PersistenceContext
    private lateinit var em: EntityManager

    @Autowired
    private lateinit var tx: TransactionTemplate

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var schedule: OrderDeliverySchedule

    @Autowired
    private lateinit var properties: OrderDeliveryProperties

    @Autowired
    private lateinit var failingTransition: FailingTransition

    @BeforeEach
    fun cleanDatabase() {
        DatabaseCleaner(jdbcTemplate).truncateAll()
        seedCourier()
    }

    @AfterEach
    fun resetFailure() {
        failingTransition.clear()
    }

    @Test
    fun `배송완료 기한이 지난 주문은 배송완료로 전이된다`() {
        val dispatchedAt = overdue()
        val orderPublicId = seedShippingOrder(dispatchedAt)

        schedule.run()

        assertEquals(OrderStatus.DELIVERED, statusOf(orderPublicId))
        assertEquals(
            dispatchedAt.plusDays(properties.autoCompleteDays),
            shipmentOf(orderPublicId).deliveredAt,
            "자동 배송완료 시각은 발송 시각 기준이어야 한다 — 배치가 늦게 돌아도 기한이 밀리지 않는다",
        )
    }

    @Test
    fun `자동 배송완료도 설정된 일수만큼 구매확정 기한을 부여한다`() {
        val dispatchedAt = overdue()
        val orderPublicId = seedShippingOrder(dispatchedAt)

        schedule.run()

        val deliveredAt = requireNotNull(shipmentOf(orderPublicId).deliveredAt)
        assertEquals(
            deliveredAt.plusDays(CONFIGURED_AUTO_CONFIRM_DAYS),
            autoConfirmDueAtOf(orderPublicId),
            "order.auto-confirm.days 설정이 무시되면 기한이 기본값으로 박힌다",
        )
    }

    @Test
    fun `자동 배송완료는 시스템이 일으킨 이력 1행을 남긴다`() {
        val orderPublicId = seedShippingOrder(overdue())

        schedule.run()

        val histories = historiesOf(orderPublicId)
        assertEquals(1, histories.size)
        assertEquals(OrderStatus.SHIPPING, histories[0].fromStatus)
        assertEquals(OrderStatus.DELIVERED, histories[0].toStatus)
        assertEquals(TriggeredBy.SYSTEM, histories[0].triggeredBy)
    }

    @Test
    fun `배송완료 기한이 안 된 주문은 전이되지 않는다`() {
        val orderPublicId = seedShippingOrder(now().minusDays(properties.autoCompleteDays - 1))

        schedule.run()

        assertEquals(OrderStatus.SHIPPING, statusOf(orderPublicId))
        assertNull(shipmentOf(orderPublicId).deliveredAt)
        assertNull(autoConfirmDueAtOf(orderPublicId), "집화 지연 경고 구간은 기한을 부여하지 않는다")
        assertEquals(0, historiesOf(orderPublicId).size)
    }

    @Test
    fun `방금 발송한 주문은 집화 지연에도 걸리지 않고 전이되지 않는다`() {
        val orderPublicId = seedShippingOrder(now().minusHours(1))

        schedule.run()

        assertEquals(OrderStatus.SHIPPING, statusOf(orderPublicId))
        assertEquals(0, historiesOf(orderPublicId).size)
    }

    @Test
    fun `두 번 돌려도 구매확정 기한이 바뀌지 않는다`() {
        val orderPublicId = seedShippingOrder(overdue())

        schedule.run()
        val firstDueAt = autoConfirmDueAtOf(orderPublicId)
        val firstDeliveredAt = shipmentOf(orderPublicId).deliveredAt
        schedule.run()

        assertEquals(firstDueAt, autoConfirmDueAtOf(orderPublicId))
        assertEquals(firstDeliveredAt, shipmentOf(orderPublicId).deliveredAt)
        assertEquals(1, historiesOf(orderPublicId).size, "재실행이 이력을 덧쌓으면 안 된다")
    }

    @Test
    fun `이미 배송완료된 주문은 대상이 아니다`() {
        val orderPublicId = seedShippingOrder(overdue())
        markDeliveredByHand(orderPublicId)

        schedule.run()

        assertEquals(0, historiesOf(orderPublicId).size, "셀러가 먼저 처리한 건은 스캔에 걸리지 않아야 한다")
    }

    @Test
    fun `같은 페이지에서 한 건이 실패해도 나머지는 전이된다`() {
        val dispatchedAt = overdue()
        val failing = seedShippingOrder(dispatchedAt)
        val healthy = seedShippingOrder(dispatchedAt)
        failingTransition.failOn(orderIdOf(failing))

        schedule.run()

        assertEquals(OrderStatus.SHIPPING, statusOf(failing))
        assertEquals(OrderStatus.DELIVERED, statusOf(healthy), "앞 건의 장애가 뒤 건을 막으면 안 된다")
    }

    @Test
    fun `스캔 상한을 채운 실패 건들이 다음 페이지를 막지 않는다`() {
        val dispatchedAt = overdue()
        val failingFirst = seedShippingOrder(dispatchedAt)
        val failingSecond = seedShippingOrder(dispatchedAt)
        val healthy = seedShippingOrder(dispatchedAt)
        failingTransition.failOn(orderIdOf(failingFirst))
        failingTransition.failOn(orderIdOf(failingSecond))

        schedule.run()

        assertEquals(OrderStatus.SHIPPING, statusOf(failingFirst))
        assertEquals(OrderStatus.SHIPPING, statusOf(failingSecond))
        assertEquals(
            OrderStatus.DELIVERED,
            statusOf(healthy),
            "앞 페이지가 전부 실패해 상태가 남으면 커서 없이는 뒷건이 영구히 처리되지 않는다",
        )
    }

    @Test
    fun `대상이 스캔 상한을 넘어도 한 회차에 모두 전이된다`() {
        val dispatchedAt = overdue()
        val orderPublicIds = List(properties.scanLimit * 2 + 1) { seedShippingOrder(dispatchedAt) }

        schedule.run()

        orderPublicIds.forEach {
            assertEquals(OrderStatus.DELIVERED, statusOf(it), "상한에서 끊으면 적체가 매 회차 남는다")
        }
    }

    // ---------- 헬퍼 ----------

    /**
     * DATETIME(6)은 마이크로초까지만 담고 그 아래를 반올림해 저장한다. 나노초 해상도 시계(리눅스)에서
     * 심은 발송 시각과 배치가 DB에서 읽은 발송 시각이 어긋나므로, 심기 전에 마이크로초로 자른다.
     */
    private fun now(): LocalDateTime = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS)

    /** 배송완료 기한을 이미 넘긴 발송 시각. */
    private fun overdue(): LocalDateTime = now().minusDays(properties.autoCompleteDays).minusHours(1)

    private fun seedCourier() {
        tx.execute { em.persist(CourierMaster.register(courierCode = "CJ", courierName = "씨제이대한통운")) }
    }

    /** 배송중 주문 1건과 그 최초 발송을 심고 주문 공개 식별자를 돌려준다. */
    private fun seedShippingOrder(dispatchedAt: LocalDateTime): String =
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
                    idempotencyKey = "delivery-batch-${System.nanoTime()}",
                    expiresAt = now().plusMinutes(10),
                )
            em.persist(group)
            val order =
                Order.create(
                    orderGroup = group,
                    sellerId = 7_777L,
                    sellerNameSnapshot = "애착상회",
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
            em.persist(
                Shipment.dispatch(
                    courier = em.find(CourierMaster::class.java, "CJ"),
                    order = order,
                    trackingNumber = "123456789012",
                    shipmentType = ShipmentType.INITIAL,
                    at = dispatchedAt,
                ),
            )
            em.flush()
            em
                .createQuery("update Order o set o.status = :status where o.id = :id")
                .setParameter("status", OrderStatus.SHIPPING)
                .setParameter("id", order.id)
                .executeUpdate()
            order.publicId
        }!!

    /** 셀러가 먼저 배송완료를 누른 상태를 재현한다 — 배치 스캔이 발송의 배송완료 시각을 보는지 가른다. */
    private fun markDeliveredByHand(orderPublicId: String) {
        tx.execute {
            em
                .createQuery("update Shipment s set s.deliveredAt = :at where s.order.publicId = :publicId")
                .setParameter("at", now())
                .setParameter("publicId", orderPublicId)
                .executeUpdate()
            em
                .createQuery("update Order o set o.status = :status where o.publicId = :publicId")
                .setParameter("status", OrderStatus.DELIVERED)
                .setParameter("publicId", orderPublicId)
                .executeUpdate()
        }
    }

    private fun orderIdOf(orderPublicId: String): Long =
        tx.execute {
            em
                .createQuery("select o.id from Order o where o.publicId = :publicId", Long::class.javaObjectType)
                .setParameter("publicId", orderPublicId)
                .singleResult
        }!!

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
                .createQuery("select s from Shipment s where s.order.publicId = :publicId", Shipment::class.java)
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

    /** 지정한 주문의 전이에서만 터지는 장치 — 건별 격리와 커서 전진이 실제로 동작하는지 가른다. */
    class FailingTransition {
        private val orderIds = ConcurrentHashMap.newKeySet<Long>()

        fun failOn(orderId: Long) {
            orderIds += orderId
        }

        fun clear() {
            orderIds.clear()
        }

        fun raiseIfTarget(orderId: Long) {
            if (orderId in orderIds) {
                throw IllegalStateException("의도된 전이 실패 (orderId=$orderId)")
            }
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    class FailingTransitionConfig {
        @Bean
        fun failingTransition(): FailingTransition = FailingTransition()

        @Bean
        @Primary
        fun failingOrderRepository(
            delegate: OrderRepositoryAdapter,
            failure: FailingTransition,
        ): OrderRepository =
            object : OrderRepository by delegate {
                override fun transitionStatusIf(
                    id: Long,
                    expected: OrderStatus,
                    next: OrderStatus,
                ): Boolean {
                    failure.raiseIfTarget(id)
                    return delegate.transitionStatusIf(id, expected, next)
                }
            }
    }
}
