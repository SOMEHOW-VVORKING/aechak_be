package com.aechak.application.order.seller.usecase.command

import com.aechak.domain.order.order.Order
import com.aechak.domain.order.shipment.CourierMaster
import com.aechak.domain.order.shipment.Shipment
import com.aechak.domain.order.shipment.enums.ShipmentType
import java.time.LocalDateTime

data class RegisterShipmentCommand(
    val sellerId: Long,
    val orderPublicId: String,
    val courierCode: String,
    val trackingNumber: String,
) {
    /** 필드 옮겨 담기만. 택배사·주문과 발송 시각은 서비스가 채우고, 최초 발송 종류는 이 유스케이스가 고정한다. */
    fun toEntity(
        courier: CourierMaster,
        order: Order,
        dispatchedAt: LocalDateTime,
    ): Shipment =
        Shipment.dispatch(
            courier = courier,
            order = order,
            trackingNumber = trackingNumber,
            shipmentType = ShipmentType.INITIAL,
            at = dispatchedAt,
        )
}
