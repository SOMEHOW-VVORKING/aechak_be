package com.aechak.domain.order.shipment.repository

import com.aechak.domain.order.shipment.Shipment
import com.aechak.domain.order.shipment.enums.ShipmentType

interface ShipmentRepository {
    fun save(shipment: Shipment): Shipment

    /** 한 주문에 최초 발송과 교환 재발송이 공존하므로 종류까지 좁혀서 찾는다. */
    fun findByOrderIdAndShipmentType(
        orderId: Long,
        shipmentType: ShipmentType,
    ): Shipment?
}
