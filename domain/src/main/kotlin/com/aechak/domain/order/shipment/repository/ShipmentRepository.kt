package com.aechak.domain.order.shipment.repository

import com.aechak.domain.order.shipment.Shipment
import com.aechak.domain.order.shipment.enums.ShipmentType
import java.time.LocalDateTime

interface ShipmentRepository {
    fun save(shipment: Shipment): Shipment

    /** 한 주문에 최초 발송과 교환 재발송이 공존하므로 종류까지 좁혀서 찾는다. */
    fun findByOrderIdAndShipmentType(
        orderId: Long,
        shipmentType: ShipmentType,
    ): Shipment?

    /**
     * 발송 시각이 기준보다 이르면서 아직 배송완료가 아닌 최초 발송을 주문과 함께 읽는다.
     * 배송중 주문만 돌려준다 — 집화 지연 경고와 자동 배송완료가 같은 스캔을 기준 시각만 달리 쓴다.
     */
    fun findPendingDispatchedBefore(
        dispatchedBefore: LocalDateTime,
        limit: Int,
    ): List<Shipment>
}
