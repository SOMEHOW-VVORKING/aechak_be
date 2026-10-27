package com.aechak.infra.client.tracking

import com.aechak.application.order.delivery.port.ShipmentTrackingPort
import com.aechak.application.order.delivery.port.ShipmentTrackingSnapshot
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Conditional
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

/**
 * 추적 포트의 목 구현. 택배사를 부르지 않고 송장번호에서 유도한 고정 단계를 돌려준다 —
 * 같은 송장은 늘 같은 값을 받고, 송장이 다르면 값도 갈린다.
 *
 * 돌려주는 문구에 표식([모의])을 박는다. 이 값이 DB에 들어가면 구매자·셀러 화면에 그대로 뜨는데,
 * 표식이 없으면 가짜 진척이 진짜 추적 정보로 읽힌다.
 *
 * 이중으로 가려 둔다 — 스위치가 기본 꺼짐이고(MockShipmentTrackingEnabled), 운영 프로파일에서는
 * 스위치를 켜도 조립되지 않는다.
 */
@Component
@Conditional(MockShipmentTrackingEnabled::class)
@Profile("!prod")
class MockShipmentTrackingAdapter : ShipmentTrackingPort {
    private val log = LoggerFactory.getLogger(javaClass)

    init {
        log.warn(
            "모의 추적 어댑터 조립됨 — 택배사를 부르지 않고 '{}' 표식이 붙은 가짜 상태를 돌려준다. 실제 추적이 아니다.",
            MOCK_PREFIX.trim(),
        )
    }

    override fun fetchTracking(
        courierCode: String,
        trackingNumber: String,
    ): ShipmentTrackingSnapshot? {
        if (trackingNumber.isBlank()) {
            return null
        }
        val lastStatus = MOCK_PREFIX + STAGES[Math.floorMod(trackingNumber.hashCode(), STAGES.size)]
        log.warn(
            "모의 추적 응답(실제 택배사 조회 아님). courierCode={}, trackingNumber={}, lastStatus={}",
            courierCode,
            trackingNumber,
            lastStatus,
        )
        return ShipmentTrackingSnapshot(lastStatus)
    }

    companion object {
        /** 저장·화면에 그대로 드러나는 표식. 값 자체에 박아 가짜 진척이 진짜로 읽히지 않게 한다 */
        const val MOCK_PREFIX = "[모의] "

        /** 송장번호 해시로 하나를 고른다 — 호출마다 흔들리면 재조회 멱등성을 볼 수 없다 */
        private val STAGES = listOf("집화완료", "간선상차", "간선하차", "배송출발", "배달완료")
    }
}
