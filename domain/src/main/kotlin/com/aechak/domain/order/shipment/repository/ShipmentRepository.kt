package com.aechak.domain.order.shipment.repository

import com.aechak.domain.order.shipment.Shipment
import com.aechak.domain.order.shipment.enums.ShipmentType
import java.time.LocalDateTime

interface ShipmentRepository {
    fun save(shipment: Shipment): Shipment

    /**
     * 추적 상태와 조회 시각만 원자 UPDATE로 기록한다. 반영한 행 수를 돌려준다(없는 발송이면 0).
     * 애그리거트를 읽어 더티체킹으로 쓰면 행 전체가 다시 써지는데, 같은 행에 배송완료 시각을 쓰는
     * 전이와 겹치면 서로 읽은 시점의 옛 값으로 상대의 컬럼을 덮는다 — 그래서 쓰는 컬럼만 짚어 쓴다.
     */
    fun updateTracking(
        shipmentId: Long,
        lastTrackingStatus: String,
        syncedAt: LocalDateTime,
    ): Int

    /** 클레임에 걸린 송장은 유형당 한 건이다 — 회수와 재발송을 유형으로 가른다. */
    fun findByClaimIdAndShipmentType(
        claimId: Long,
        shipmentType: ShipmentType,
    ): Shipment?

    /** 한 주문에 최초 발송과 교환 재발송이 공존하므로 종류까지 좁혀서 찾는다. */
    fun findByOrderIdAndShipmentType(
        orderId: Long,
        shipmentType: ShipmentType,
    ): Shipment?

    /**
     * 발송 시각이 기준보다 이르면서 아직 배송완료가 아닌 최초 발송을 주문과 함께 읽는다.
     * 배송중 주문만 돌려주고, 발송 식별자가 커서보다 큰 것만 돌려준다(오름차순).
     */
    fun findPendingDispatchedBefore(
        dispatchedBefore: LocalDateTime,
        afterShipmentId: Long,
        limit: Int,
    ): List<Shipment>

    /**
     * 추적을 갱신할 최초 발송 — 발송됐고 아직 배송완료가 아닌 배송중 주문의 것.
     * 택배사와 주문을 함께 읽는다(추적 조회에 택배사 코드와 송장번호가 필요하다).
     * 발송 식별자가 커서보다 큰 것만 오름차순으로 돌려준다.
     */
    fun findTrackingTargets(
        afterShipmentId: Long,
        limit: Int,
    ): List<Shipment>
}
