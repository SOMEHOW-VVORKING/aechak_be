package com.aechak.batch.order

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 추적 갱신 배치 설정. [enabled] 기본값은 꺼진 상태다 — 지금 조립할 수 있는 추적 어댑터가 목뿐이라
 * 켜면 가짜 상태 문구가 DB에 들어가 구매자·셀러 화면에 뜬다. 실제 택배사 어댑터가 붙은 환경에서만 켠다.
 */
@ConfigurationProperties("order.delivery.tracking")
data class ShipmentTrackingProperties(
    val enabled: Boolean = false,
    /** 한 페이지에 읽을 최대 건수. 회차는 커서로 페이지를 넘기며 대상을 모두 처리한다. */
    val scanLimit: Int = DEFAULT_SCAN_LIMIT,
) {
    companion object {
        const val DEFAULT_SCAN_LIMIT = 200
    }
}
