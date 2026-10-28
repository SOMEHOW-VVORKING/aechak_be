package com.aechak.api.order.response

import com.aechak.application.order.usecase.result.OrderActiveClaimResult
import com.aechak.application.order.usecase.result.OrderDeliveryAddressResult
import com.aechak.application.order.usecase.result.OrderDetailResult
import com.aechak.application.order.usecase.result.OrderPaymentResult
import com.aechak.application.order.usecase.result.OrderShipmentResult
import com.aechak.domain.order.claim.enums.ClaimStatus
import com.aechak.domain.order.claim.enums.ClaimType
import com.aechak.domain.order.claim.enums.RefundStatus
import com.aechak.domain.order.order.enums.OrderStatus
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId

data class OrderDetailResponse(
    val orderId: String,
    val orderGroupId: String,
    val orderedAt: OffsetDateTime,
    val status: OrderStatus,
    val sellerName: String?,
    val items: List<OrderLineItemResponse>,
    val payment: OrderPaymentResponse,
    val deliveryAddress: OrderDeliveryAddressResponse,
    val shipment: OrderShipmentResponse?,
    val activeClaim: OrderActiveClaimResponse?,
    val purchaseConfirmDueAt: OffsetDateTime?,
) {
    companion object {
        fun from(result: OrderDetailResult): OrderDetailResponse =
            OrderDetailResponse(
                orderId = result.orderId,
                orderGroupId = result.orderGroupId,
                orderedAt = result.orderedAt.atZone(ZoneId.systemDefault()).toOffsetDateTime(),
                status = result.status,
                sellerName = result.sellerName,
                items = result.items.map(OrderLineItemResponse::from),
                payment = OrderPaymentResponse.from(result.payment),
                deliveryAddress = OrderDeliveryAddressResponse.from(result.deliveryAddress),
                shipment = result.shipment?.let(OrderShipmentResponse::from),
                activeClaim = result.activeClaim?.let(OrderActiveClaimResponse::from),
                purchaseConfirmDueAt = result.purchaseConfirmDueAt?.toOffset(),
            )
    }
}

data class OrderPaymentResponse(
    val sellerShippingFee: Long,
    val totalProductAmount: Long,
    val totalShippingFee: Long,
    val couponDiscountAmount: Long,
    val usedPoint: Long,
    val finalPaymentAmount: Long,
    val paymentMethod: String?,
) {
    companion object {
        fun from(result: OrderPaymentResult): OrderPaymentResponse =
            OrderPaymentResponse(
                sellerShippingFee = result.sellerShippingFee,
                totalProductAmount = result.totalProductAmount,
                totalShippingFee = result.totalShippingFee,
                couponDiscountAmount = result.couponDiscountAmount,
                usedPoint = result.usedPoint,
                finalPaymentAmount = result.finalPaymentAmount,
                paymentMethod = result.paymentMethod,
            )
    }
}

data class OrderDeliveryAddressResponse(
    val receiverName: String,
    val contactNumber: String,
    val zipCode: String,
    val baseAddress: String,
    val detailAddress: String?,
    val deliveryMemo: String?,
) {
    companion object {
        fun from(result: OrderDeliveryAddressResult): OrderDeliveryAddressResponse =
            OrderDeliveryAddressResponse(
                receiverName = result.receiverName,
                contactNumber = result.contactNumber,
                zipCode = result.zipCode,
                baseAddress = result.baseAddress,
                detailAddress = result.detailAddress,
                deliveryMemo = result.deliveryMemo,
            )
    }
}

data class OrderShipmentResponse(
    val courierCode: String,
    val courierName: String,
    val trackingNumber: String,
    val dispatchedAt: OffsetDateTime?,
    val deliveredAt: OffsetDateTime?,
    @field:Schema(description = "택배사가 준 마지막 추적 문구. 고정 어휘가 아니라 자유 문자열이다.")
    val lastTrackingStatus: String?,
) {
    companion object {
        fun from(result: OrderShipmentResult): OrderShipmentResponse =
            OrderShipmentResponse(
                courierCode = result.courierCode,
                courierName = result.courierName,
                trackingNumber = result.trackingNumber,
                dispatchedAt = result.dispatchedAt?.toOffset(),
                deliveredAt = result.deliveredAt?.toOffset(),
                lastTrackingStatus = result.lastTrackingStatus,
            )
    }
}

data class OrderActiveClaimResponse(
    val claimPublicId: String,
    val claimType: ClaimType,
    val status: ClaimStatus,
    val refundStatus: RefundStatus?,
) {
    companion object {
        fun from(result: OrderActiveClaimResult): OrderActiveClaimResponse =
            OrderActiveClaimResponse(
                claimPublicId = result.claimPublicId,
                claimType = result.claimType,
                status = result.status,
                refundStatus = result.refundStatus,
            )
    }
}

private fun LocalDateTime.toOffset(): OffsetDateTime = atZone(ZoneId.systemDefault()).toOffsetDateTime()
