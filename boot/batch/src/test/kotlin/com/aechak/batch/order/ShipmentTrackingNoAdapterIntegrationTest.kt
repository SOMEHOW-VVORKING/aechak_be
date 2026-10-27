package com.aechak.batch.order

import com.aechak.application.order.delivery.port.ShipmentTrackingPort
import com.aechak.application.order.delivery.usecase.ShipmentTrackingUseCase
import com.aechak.application.order.delivery.usecase.command.SyncShipmentTrackingCommand
import com.aechak.application.order.delivery.usecase.result.ShipmentTrackingSyncOutcome
import com.aechak.batch.support.BatchIntegrationTestBase
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import org.springframework.test.context.TestPropertySource
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 추적 어댑터가 없는데 배치를 켠 환경. 포트 구현이 목뿐이고 그 목이 기본 꺼짐이라 실제로 일어나는 조합이다 —
 * 그때 부팅이 깨지지도, 아무 일 없는 척 성공하지도 않아야 한다. 어댑터 부재는 결과로 드러나고
 * 호출한 배치가 그것을 보고 회차를 끊는다.
 */
@TestPropertySource(
    properties = [
        "order.delivery.tracking.enabled=true",
        "order.delivery.tracking.cron=-", // 타이머 등록만 끈다 — 테스트가 run()을 직접 부른다
    ],
)
class ShipmentTrackingNoAdapterIntegrationTest : BatchIntegrationTestBase() {
    @Autowired
    private lateinit var schedule: ShipmentTrackingSchedule

    @Autowired
    private lateinit var useCase: ShipmentTrackingUseCase

    @Autowired
    private lateinit var context: ApplicationContext

    @BeforeEach
    fun seedCourier() {
        ShipmentTrackingFixture.seedCourier(em, tx)
    }

    @Test
    fun `목 어댑터가 꺼진 컨텍스트에도 추적 갱신 파사드는 조립된다`() {
        // 선택 주입이 깨지면 이 컨텍스트가 뜨지 않는다 — 루트 스캔인 api도 같은 이유로 깨진다
        assertTrue(
            context.getBeansOfType(ShipmentTrackingPort::class.java).isEmpty(),
            "목 어댑터가 꺼져 있어야 어댑터 부재 경로를 볼 수 있다",
        )
    }

    @Test
    fun `어댑터가 없으면 갱신하지 않고 어댑터 부재를 결과로 알린다`() {
        // 어댑터 확인이 DB 접근보다 앞이라 존재하지 않는 발송 식별자로도 계약을 볼 수 있다
        val result = useCase.syncTracking(SyncShipmentTrackingCommand(shipmentId = 999_999L, courierCode = "CJ", trackingNumber = "1"))

        assertEquals(ShipmentTrackingSyncOutcome.NO_ADAPTER, result.outcome)
        assertNull(result.lastStatus)
    }

    @Test
    fun `어댑터가 없으면 대상이 있어도 아무 값도 쓰지 않고 회차를 끊는다`() {
        val orderPublicId = ShipmentTrackingFixture.seedShippingOrder(em, tx, trackingNumber = "300000000001")

        schedule.run()

        val shipment = ShipmentTrackingFixture.shipmentOf(em, tx, orderPublicId)
        assertNull(shipment.lastTrackingStatus, "어댑터 없이 돈 회차가 값을 쓰면 출처 없는 상태가 화면에 뜬다")
        assertNull(shipment.lastSyncedAt, "조회하지 않았는데 조회 시각이 남으면 다음 사람이 갱신된 것으로 읽는다")
    }
}
