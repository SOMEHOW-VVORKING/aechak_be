package com.aechak.infra.client.tracking

import com.aechak.application.order.delivery.port.ShipmentTrackingPort
import org.assertj.core.api.Assertions.assertThat
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 계약 둘. ① 목 어댑터는 스위치를 켠 비운영 환경에서만 조립된다 — 깨지면 가짜 추적이 운영 DB에 들어간다.
 * ② 돌려주는 문구에 표식이 박혀 있고 송장번호마다 값이 고정된다 — 표식이 사라지면 화면에서 진짜 추적과
 * 구분되지 않고, 값이 흔들리면 같은 송장을 다시 물을 때마다 진척이 오락가락한다.
 */
class MockShipmentTrackingAdapterTest {
    private val runner = ApplicationContextRunner().withUserConfiguration(TrackingClientScan::class.java)

    @Configuration(proxyBeanMethods = false)
    @ComponentScan("com.aechak.infra.client.tracking")
    class TrackingClientScan

    @Test
    fun `스위치를 적지 않으면 추적 포트 빈이 조립되지 않는다`() {
        runner.run { context ->
            assertThat(context).doesNotHaveBean(ShipmentTrackingPort::class.java)
        }
    }

    @Test
    fun `스위치를 끄면 추적 포트 빈이 조립되지 않는다`() {
        runner.withPropertyValues("${MockShipmentTrackingEnabled.PROPERTY}=false").run { context ->
            assertThat(context).doesNotHaveBean(ShipmentTrackingPort::class.java)
        }
    }

    @Test
    fun `스위치를 켠 비운영 프로파일에서는 목 어댑터가 조립된다`() {
        runner
            .withPropertyValues(
                "spring.profiles.active=local",
                "${MockShipmentTrackingEnabled.PROPERTY}=true",
            ).run { context ->
                assertThat(context).hasSingleBean(ShipmentTrackingPort::class.java)
                assertThat(context).hasSingleBean(MockShipmentTrackingAdapter::class.java)
            }
    }

    @Test
    fun `운영 프로파일에서는 스위치를 켜도 목 어댑터가 조립되지 않는다`() {
        runner
            .withPropertyValues(
                "spring.profiles.active=prod",
                "${MockShipmentTrackingEnabled.PROPERTY}=true",
            ).run { context ->
                assertThat(context).doesNotHaveBean(ShipmentTrackingPort::class.java)
            }
    }

    @Test
    fun `목 응답의 상태 문구에는 모의 표식이 박혀 있다`() {
        val snapshot = MockShipmentTrackingAdapter().fetchTracking("CJ", "123456789012")

        assertNotNull(snapshot)
        assertTrue(
            snapshot.lastStatus.startsWith(MockShipmentTrackingAdapter.MOCK_PREFIX),
            "표식이 없으면 화면에서 진짜 추적 정보와 구분되지 않는다 (${snapshot.lastStatus})",
        )
    }

    @Test
    fun `같은 송장번호는 늘 같은 상태를 받는다`() {
        val adapter = MockShipmentTrackingAdapter()

        val first = adapter.fetchTracking("CJ", "123456789012")
        val second = adapter.fetchTracking("CJ", "123456789012")

        assertEquals(first, second)
    }

    @Test
    fun `송장번호가 비면 조회 실패로 돌려준다`() {
        assertNull(MockShipmentTrackingAdapter().fetchTracking("CJ", " "))
    }
}
