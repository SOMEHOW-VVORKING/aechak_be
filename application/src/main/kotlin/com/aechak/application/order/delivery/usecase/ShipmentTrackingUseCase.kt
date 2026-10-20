package com.aechak.application.order.delivery.usecase

import com.aechak.application.order.delivery.usecase.command.SyncShipmentTrackingCommand
import com.aechak.application.order.delivery.usecase.result.ShipmentTrackingSyncResult
import com.aechak.application.order.delivery.usecase.result.ShipmentTrackingTargetResult

/**
 * 택배사 추적 갱신 진입점. 시간만 보고 전이하는 OrderDeliveryUseCase와 나눠 둔다 —
 * 이쪽만 외부(택배사) 포트를 요구하므로 한 타입에 두면 배송완료 전이를 쓰는 셀러센터까지
 * 추적 어댑터를 조립해야 한다.
 */
interface ShipmentTrackingUseCase {
    /** 발송됐고 아직 배송완료가 아닌 최초 발송. 발송 식별자 오름차순, 커서보다 큰 것만. */
    fun findTrackingTargets(
        afterShipmentId: Long,
        limit: Int,
    ): List<ShipmentTrackingTargetResult>

    /** 송장 하나의 추적을 조회해 발송에 반영한다. 상태 전이는 일으키지 않는다. */
    fun syncTracking(command: SyncShipmentTrackingCommand): ShipmentTrackingSyncResult
}
