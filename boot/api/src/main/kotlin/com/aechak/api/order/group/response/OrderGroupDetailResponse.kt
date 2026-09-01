package com.aechak.api.order.group.response

import com.aechak.api.order.response.OrderLineItemResponse
import com.aechak.application.order.usecase.result.OrderGroupDetailResult
import java.time.OffsetDateTime
import java.time.ZoneId

data class OrderGroupDetailResponse(
    val orderGroupId: String,
    val status: String,
    val totalProductAmount: Long,
    val totalShippingFee: Long,
    val usedPoint: Long,
    val finalPaymentAmount: Long,
    /** 절대 시각. status가 PENDING_PAYMENT여도 이 시각이 지났으면 만료된 그룹임 */
    val expiresAt: OffsetDateTime,
    val deliveryAddress: DeliveryAddressSnapshotResponse,
    val orders: List<SellerOrderResponse>,
) {
    data class DeliveryAddressSnapshotResponse(
        val receiverName: String,
        val contactNumber: String,
        val zipCode: String,
        val baseAddress: String,
        val detailAddress: String?,
        val deliveryMemo: String?,
    ) {
        companion object {
            fun from(address: OrderGroupDetailResult.DeliveryAddressSnapshotResult): DeliveryAddressSnapshotResponse =
                DeliveryAddressSnapshotResponse(
                    receiverName = address.receiverName,
                    contactNumber = address.contactNumber,
                    zipCode = address.zipCode,
                    baseAddress = address.baseAddress,
                    detailAddress = address.detailAddress,
                    deliveryMemo = address.deliveryMemo,
                )
        }
    }

    data class SellerOrderResponse(
        val orderId: String,
        val status: String,
        val sellerName: String?,
        val sellerShippingFee: Long,
        val items: List<OrderLineItemResponse>,
    ) {
        companion object {
            fun from(order: OrderGroupDetailResult.SellerOrderResult): SellerOrderResponse =
                SellerOrderResponse(
                    orderId = order.orderId,
                    status = order.status.name,
                    sellerName = order.sellerName,
                    sellerShippingFee = order.sellerShippingFee,
                    items = order.items.map(OrderLineItemResponse::from),
                )
        }
    }

    companion object {
        fun from(result: OrderGroupDetailResult): OrderGroupDetailResponse =
            OrderGroupDetailResponse(
                orderGroupId = result.orderGroupId,
                status = result.status.name,
                totalProductAmount = result.totalProductAmount,
                totalShippingFee = result.totalShippingFee,
                usedPoint = result.usedPoint,
                finalPaymentAmount = result.finalPaymentAmount,
                expiresAt = result.expiresAt.atZone(ZoneId.systemDefault()).toOffsetDateTime(),
                deliveryAddress = DeliveryAddressSnapshotResponse.from(result.deliveryAddress),
                orders = result.orders.map(SellerOrderResponse::from),
            )
    }
}
