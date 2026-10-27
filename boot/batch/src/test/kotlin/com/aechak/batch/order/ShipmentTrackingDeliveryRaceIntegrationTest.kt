package com.aechak.batch.order

import com.aechak.application.order.delivery.service.OrderDeliveryService
import com.aechak.batch.support.BatchIntegrationTestBase
import com.aechak.domain.order.shipment.Shipment
import jakarta.persistence.EntityManagerFactory
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * 발송 행에 쓰는 두 경로가 서로의 컬럼을 지우지 않는지. 추적 갱신은 상태 문구·조회 시각을 쓰고
 * 배송완료 전이는 배송완료 시각을 쓴다. 전이는 애그리거트를 읽어 더티체킹으로 쓰므로 행 전체가 다시 써지는데,
 * 추적 두 컬럼이 그 UPDATE에 실리면 전이가 읽은 시점의 값(처음에는 null)으로 방금 반영된 추적 결과를 덮는다.
 * 추적 문구는 구매자 주문 상세와 셀러 주문 목록에 그대로 실리므로 조용히 비워지면 화면에서 사라진다.
 *
 * 스레드 없이 영속성 컨텍스트를 겹쳐 순서를 직접 만든다 — 전이가 발송을 읽은 뒤, 플러시하기 전에
 * 추적이 커밋되는 순서가 경합의 실체다.
 */
class ShipmentTrackingDeliveryRaceIntegrationTest : BatchIntegrationTestBase() {
    @Autowired
    private lateinit var orderDeliveryService: OrderDeliveryService

    @Autowired
    private lateinit var entityManagerFactory: EntityManagerFactory

    @BeforeEach
    fun seedCourier() {
        ShipmentTrackingFixture.seedCourier(em, tx)
    }

    @Test
    fun `배송완료 전이는 그 사이 커밋된 추적 상태를 지우지 않는다`() {
        val orderPublicId = ShipmentTrackingFixture.seedShippingOrder(em, tx, trackingNumber = "400000000001")
        val shipmentId = ShipmentTrackingFixture.shipmentOf(em, tx, orderPublicId).id

        val transition = entityManagerFactory.createEntityManager()
        try {
            // 1) 전이가 발송을 읽는다 — 이 스냅샷에는 추적 문구가 아직 없다
            transition.transaction.begin()
            val readByTransition = transition.find(Shipment::class.java, shipmentId)
            assertEquals(null, readByTransition.lastTrackingStatus, "읽은 시점에 추적 문구가 이미 있으면 경합이 재현되지 않는다")

            // 2) 그 사이 추적 갱신이 들어와 커밋한다
            tx.execute { orderDeliveryService.applyTracking(shipmentId, "배송출발", ShipmentTrackingFixture.now()) }

            // 3) 전이가 자기 스냅샷으로 발송 행을 다시 쓴다
            readByTransition.markDelivered(ShipmentTrackingFixture.now())
            transition.transaction.commit()
        } finally {
            if (transition.transaction.isActive) {
                transition.transaction.rollback()
            }
            transition.close()
        }

        val shipment = ShipmentTrackingFixture.shipmentOf(em, tx, orderPublicId)
        assertEquals(
            "배송출발",
            shipment.lastTrackingStatus,
            "전이의 UPDATE가 추적 컬럼까지 실으면 방금 반영된 문구가 화면에서 사라진다",
        )
        assertNotNull(shipment.lastSyncedAt, "문구만 남고 조회 시각이 지워지면 언제 기준의 상태인지 알 수 없다")
        assertNotNull(shipment.deliveredAt, "추적 값을 지키려고 배송완료 시각을 못 쓰면 반대쪽을 잃는다")
    }

    @Test
    fun `추적 반영은 그 사이 커밋된 배송완료 시각을 지우지 않는다`() {
        val orderPublicId = ShipmentTrackingFixture.seedShippingOrder(em, tx, trackingNumber = "400000000002")
        val shipmentId = ShipmentTrackingFixture.shipmentOf(em, tx, orderPublicId).id
        val deliveredAt = ShipmentTrackingFixture.now()

        // 반영 경로가 애그리거트를 읽어 더티체킹으로 쓰면 반대 방향으로 같은 사고가 난다
        tx.execute { em.find(Shipment::class.java, shipmentId).markDelivered(deliveredAt) }
        tx.execute { orderDeliveryService.applyTracking(shipmentId, "배달완료", ShipmentTrackingFixture.now()) }

        val shipment = ShipmentTrackingFixture.shipmentOf(em, tx, orderPublicId)
        assertEquals(deliveredAt, shipment.deliveredAt, "추적 반영이 배송완료 시각을 되돌리면 구매확정 기한의 기준이 사라진다")
        assertEquals("배달완료", shipment.lastTrackingStatus)
    }
}
