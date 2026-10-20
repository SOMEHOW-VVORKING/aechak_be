package com.aechak.batch.order

import com.aechak.application.order.delivery.port.ShipmentTrackingPort
import com.aechak.application.order.delivery.port.ShipmentTrackingSnapshot
import com.aechak.batch.support.BatchIntegrationTestBase
import com.aechak.domain.order.group.DeliveryAddressSnapshot
import com.aechak.domain.order.group.OrderGroup
import com.aechak.domain.order.order.Order
import com.aechak.domain.order.order.OrderItem
import com.aechak.domain.order.order.enums.OrderStatus
import com.aechak.domain.order.shipment.CourierMaster
import com.aechak.domain.order.shipment.Shipment
import com.aechak.domain.order.shipment.enums.ShipmentType
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.test.context.TestPropertySource
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * 추적 갱신 배치 통합. 이 배치의 계약은 둘이다 — 대상 선별(발송됐고 미배송인 최초 발송)과
 * **상태 전이를 일으키지 않는 것**. 추적 문구가 주문 상태를 움직이면 택배사 문구 한 줄로 구매확정 기한이 열린다.
 */
@Import(ShipmentTrackingScheduleIntegrationTest.FakeTrackingConfig::class)
@TestPropertySource(
    properties = [
        "order.delivery.tracking.enabled=true",
        "order.delivery.tracking.cron=-", // 타이머 등록만 끈다 — 테스트가 run()을 직접 부른다
        "order.delivery.tracking.scan-limit=2", // 페이지 경계를 실제로 넘게 — 상한에서 끊는 회귀를 잡는다
    ],
)
class ShipmentTrackingScheduleIntegrationTest : BatchIntegrationTestBase() {
    @Autowired
    private lateinit var schedule: ShipmentTrackingSchedule

    @Autowired
    private lateinit var tracking: FakeShipmentTracking

    @BeforeEach
    fun seedCourier() {
        tx.execute { em.persist(CourierMaster.register(courierCode = "CJ", courierName = "씨제이대한통운")) }
    }

    @AfterEach
    fun resetTracking() {
        tracking.clear()
    }

    @Test
    fun `발송된 배송에 추적 상태와 조회 시각이 채워진다`() {
        val orderPublicId = seedShippingOrder(trackingNumber = "100000000001")
        tracking.respond("100000000001", "배송출발")

        schedule.run()

        val shipment = shipmentOf(orderPublicId)
        assertEquals("배송출발", shipment.lastTrackingStatus)
        assertNotNull(shipment.lastSyncedAt, "조회 시각을 남기지 않으면 언제 기준의 상태인지 알 수 없다")
    }

    @Test
    fun `추적 갱신은 주문 상태와 배송완료 시각을 건드리지 않는다`() {
        val orderPublicId = seedShippingOrder(trackingNumber = "100000000002")
        tracking.respond("100000000002", "배달완료")

        schedule.run()

        assertEquals(OrderStatus.SHIPPING, statusOf(orderPublicId), "추적 문구가 주문 상태를 움직이면 안 된다")
        assertNull(shipmentOf(orderPublicId).deliveredAt)
        assertNull(autoConfirmDueAtOf(orderPublicId), "추적만으로 구매확정 기한이 열리면 안 된다")
    }

    @Test
    fun `택배사가 송장을 모르면 상태는 비어 있는 채로 남는다`() {
        val orderPublicId = seedShippingOrder(trackingNumber = "100000000003")

        schedule.run()

        assertNull(shipmentOf(orderPublicId).lastTrackingStatus)
        assertNull(shipmentOf(orderPublicId).lastSyncedAt)
    }

    @Test
    fun `이미 배송완료된 발송은 대상이 아니다`() {
        val orderPublicId = seedShippingOrder(trackingNumber = "100000000004")
        tracking.respond("100000000004", "배달완료")
        markDeliveredByHand(orderPublicId)

        schedule.run()

        assertNull(shipmentOf(orderPublicId).lastTrackingStatus, "끝난 배송을 계속 조회하면 택배사 호출이 영구히 늘어난다")
    }

    @Test
    fun `한 건의 조회 실패가 나머지 건의 갱신을 막지 않는다`() {
        val failing = seedShippingOrder(trackingNumber = "100000000005")
        val healthy = seedShippingOrder(trackingNumber = "100000000006")
        tracking.failOn("100000000005")
        tracking.respond("100000000006", "간선하차")

        schedule.run()

        assertNull(shipmentOf(failing).lastTrackingStatus)
        assertEquals("간선하차", shipmentOf(healthy).lastTrackingStatus, "앞 건의 장애가 뒤 건을 막으면 안 된다")
    }

    @Test
    fun `대상이 스캔 상한을 넘어도 한 회차에 모두 갱신된다`() {
        val orderPublicIds =
            (1..5).map { index ->
                val trackingNumber = "20000000000$index"
                tracking.respond(trackingNumber, "배송출발")
                seedShippingOrder(trackingNumber = trackingNumber)
            }

        schedule.run()

        orderPublicIds.forEach {
            assertEquals("배송출발", shipmentOf(it).lastTrackingStatus, "상한에서 끊으면 적체가 매 회차 남는다")
        }
    }

    // ---------- 헬퍼 ----------

    /** DATETIME(6)은 마이크로초까지만 담으므로 심기 전에 잘라 둔다 — 나노초 시계에서 심은 값과 읽은 값이 어긋난다. */
    private fun now(): LocalDateTime = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS)

    private fun seedShippingOrder(trackingNumber: String): String =
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
                    idempotencyKey = "tracking-batch-${System.nanoTime()}",
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
                    trackingNumber = trackingNumber,
                    shipmentType = ShipmentType.INITIAL,
                    at = now().minusDays(1),
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

    private fun shipmentOf(orderPublicId: String): Shipment =
        tx.execute {
            em
                .createQuery("select s from Shipment s where s.order.publicId = :publicId", Shipment::class.java)
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

    /** 송장번호별로 응답을 미리 심어 두는 추적 포트. 심지 않은 송장은 택배사가 모르는 것으로 돌려준다. */
    class FakeShipmentTracking : ShipmentTrackingPort {
        private val responses = ConcurrentHashMap<String, String>()
        private val failures = ConcurrentHashMap.newKeySet<String>()

        fun respond(
            trackingNumber: String,
            lastStatus: String,
        ) {
            responses[trackingNumber] = lastStatus
        }

        fun failOn(trackingNumber: String) {
            failures += trackingNumber
        }

        fun clear() {
            responses.clear()
            failures.clear()
        }

        override fun fetchTracking(
            courierCode: String,
            trackingNumber: String,
        ): ShipmentTrackingSnapshot? {
            if (trackingNumber in failures) {
                throw IllegalStateException("의도된 추적 조회 실패 (trackingNumber=$trackingNumber)")
            }
            return responses[trackingNumber]?.let { ShipmentTrackingSnapshot(it) }
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    class FakeTrackingConfig {
        /** 목 어댑터는 기본 꺼짐이라 조립되지 않는다 — 테스트가 포트 구현을 직접 공급한다. */
        @Bean
        fun fakeShipmentTracking(): FakeShipmentTracking = FakeShipmentTracking()
    }
}
