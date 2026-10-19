package com.aechak.application.order.delivery.usecase.result

import com.aechak.domain.order.shipment.Shipment
import java.time.LocalDateTime

data class PendingDeliveryResult(
    val shipmentId: Long,
    val orderId: Long,
    val orderPublicId: String,
    val dispatchedAt: LocalDateTime,
) {
    companion object {
        fun from(shipment: Shipment): PendingDeliveryResult =
            PendingDeliveryResult(
                shipmentId = shipment.id,
                orderId = shipment.order.id,
                orderPublicId = shipment.order.publicId,
                dispatchedAt =
                    requireNotNull(shipment.dispatchedAt) {
                        "발송 시각이 없는 발송은 배송완료 대상이 아니다 (shipmentId=${shipment.id})"
                    },
            )
    }
}
