package com.aechak.application.order.delivery.usecase.result

/** 추적 갱신 한 건의 결과. */
data class ShipmentTrackingSyncResult(
    val shipmentId: Long,
    val outcome: ShipmentTrackingSyncOutcome,
    /** 반영한 상태 문구. 갱신하지 않았으면 null */
    val lastStatus: String?,
) {
    companion object {
        fun updated(
            shipmentId: Long,
            lastStatus: String,
        ): ShipmentTrackingSyncResult = ShipmentTrackingSyncResult(shipmentId, ShipmentTrackingSyncOutcome.UPDATED, lastStatus)

        fun notFound(shipmentId: Long): ShipmentTrackingSyncResult =
            ShipmentTrackingSyncResult(shipmentId, ShipmentTrackingSyncOutcome.TRACKING_NOT_FOUND, null)

        fun noAdapter(shipmentId: Long): ShipmentTrackingSyncResult =
            ShipmentTrackingSyncResult(shipmentId, ShipmentTrackingSyncOutcome.NO_ADAPTER, null)
    }
}

enum class ShipmentTrackingSyncOutcome {
    /** 추적 결과를 받아 발송에 반영했다 */
    UPDATED,

    /** 택배사가 그 송장을 아직 모른다 — 다음 회차가 다시 묻는다 */
    TRACKING_NOT_FOUND,

    /**
     * 이 실행 모듈에 추적 어댑터가 조립되지 않았다. 추적 연동이 실물로 붙으면 사라질 값이다 —
     * 지금은 포트만 있고 구현이 목뿐이라, 어댑터 없이 켜진 배치가 조용히 도는 대신 이 값으로 드러난다.
     */
    NO_ADAPTER,
}
