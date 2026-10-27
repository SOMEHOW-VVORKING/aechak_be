package com.aechak.batch.order

import com.aechak.application.order.delivery.usecase.ShipmentTrackingUseCase
import com.aechak.application.order.delivery.usecase.command.SyncShipmentTrackingCommand
import com.aechak.application.order.delivery.usecase.query.ShipmentTrackingScanQuery
import com.aechak.application.order.delivery.usecase.result.ShipmentTrackingSyncResult
import com.aechak.application.order.delivery.usecase.result.ShipmentTrackingTargetResult
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.ApplicationContext
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 추적 갱신 스케줄의 조립 조건. 지금 붙을 수 있는 추적 어댑터는 목뿐이라, 이 스케줄이 저절로 켜지면
 * 표식이 붙은 가짜 상태가 DB에 쌓이고 구매자·셀러 화면에 뜬다 — 기본 꺼짐이 계약이다.
 * 컨텍스트를 띄우지 않고 조건만 본다.
 */
class ShipmentTrackingScheduleConditionTest {
    private val runner =
        ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(PropertyPlaceholderAutoConfiguration::class.java))
            .withBean(ShipmentTrackingUseCase::class.java, { NoopTracking() })
            .withBean(ShipmentTrackingProperties::class.java, { ShipmentTrackingProperties() })
            .withUserConfiguration(ShipmentTrackingSchedule::class.java)

    @Test
    fun `스위치를 적지 않으면 조립되지 않는다`() {
        runner.run { context -> assertEquals(0, scheduleCount(context), "기본값이 켜지면 목 추적 값이 DB에 들어간다") }
    }

    @Test
    fun `스위치를 끄면 조립되지 않는다`() {
        runner
            .withPropertyValues("order.delivery.tracking.enabled=false")
            .run { context -> assertEquals(0, scheduleCount(context)) }
    }

    @Test
    fun `스위치를 켜면 조립된다`() {
        runner
            .withPropertyValues("order.delivery.tracking.enabled=true")
            .run { context -> assertEquals(1, scheduleCount(context)) }
    }

    private fun scheduleCount(context: ApplicationContext): Int = context.getBeanNamesForType(ShipmentTrackingSchedule::class.java).size

    private class NoopTracking : ShipmentTrackingUseCase {
        override fun findTrackingTargets(query: ShipmentTrackingScanQuery): List<ShipmentTrackingTargetResult> = emptyList()

        override fun syncTracking(command: SyncShipmentTrackingCommand): ShipmentTrackingSyncResult =
            ShipmentTrackingSyncResult.notFound(command.shipmentId)
    }
}
