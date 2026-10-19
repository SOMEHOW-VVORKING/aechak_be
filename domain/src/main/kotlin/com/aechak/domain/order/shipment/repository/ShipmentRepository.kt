package com.aechak.domain.order.shipment.repository

import com.aechak.domain.order.shipment.Shipment
import com.aechak.domain.order.shipment.enums.ShipmentType

interface ShipmentRepository {
    fun save(shipment: Shipment): Shipment

    /** 클레임에 걸린 송장은 유형당 한 건이다 — 회수와 재발송을 유형으로 가른다. */
    fun findByClaimIdAndShipmentType(
        claimId: Long,
        shipmentType: ShipmentType,
    ): Shipment?
}
