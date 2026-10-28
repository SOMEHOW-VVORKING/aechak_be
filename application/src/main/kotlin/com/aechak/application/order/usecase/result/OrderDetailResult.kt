package com.aechak.application.order.usecase.result

import com.aechak.application.order.port.view.OrderActiveClaimView
import com.aechak.application.order.port.view.OrderDeliveryAddressView
import com.aechak.application.order.port.view.OrderDetailView
import com.aechak.application.order.port.view.OrderLineView
import com.aechak.application.order.port.view.OrderShipmentView
import com.aechak.domain.order.claim.enums.ClaimStatus
import com.aechak.domain.order.claim.enums.ClaimType
import com.aechak.domain.order.claim.enums.RefundStatus
import com.aechak.domain.order.order.enums.OrderStatus
import java.time.LocalDateTime

data class OrderDetailResult(
    val orderId: String,
    val orderGroupId: String,
    val orderedAt: LocalDateTime,
    val status: OrderStatus,
    val sellerName: String?,
    val items: List<OrderLineItemResult>,
    val payment: OrderPaymentResult,
    val deliveryAddress: OrderDeliveryAddressResult,
    val shipment: OrderShipmentResult?,
    val activeClaim: OrderActiveClaimResult?,
    val purchaseConfirmDueAt: LocalDateTime?,
) {
    companion object {
        fun of(
            view: OrderDetailView,
            lines: List<OrderLineView>,
            shipment: OrderShipmentView?,
            activeClaim: OrderActiveClaimView?,
            resolveThumbnail: (String?) -> String?,
            decryptPii: (String) -> String,
        ): OrderDetailResult =
            OrderDetailResult(
                orderId = view.orderPublicId,
                orderGroupId = view.orderGroupPublicId,
                orderedAt = view.orderedAt,
                status = view.status,
                sellerName = view.sellerName,
                items = lines.map { OrderLineItemResult.of(it, resolveThumbnail(it.thumbnailKey)) },
                payment = OrderPaymentResult.from(view),
                deliveryAddress = OrderDeliveryAddressResult.of(view.deliveryAddress, decryptPii),
                shipment = shipment?.let(OrderShipmentResult::from),
                activeClaim = activeClaim?.let(OrderActiveClaimResult::from),
                purchaseConfirmDueAt = view.purchaseConfirmDueAt,
            )
    }
}

/** sellerShippingFee만 이 셀러 몫이고 나머지는 결제 단위인 주문그룹 값. */
data class OrderPaymentResult(
    val sellerShippingFee: Long,
    val totalProductAmount: Long,
    val totalShippingFee: Long,
    val couponDiscountAmount: Long,
    val usedPoint: Long,
    val finalPaymentAmount: Long,
    val paymentMethod: String?,
) {
    companion object {
        fun from(view: OrderDetailView): OrderPaymentResult =
            OrderPaymentResult(
                sellerShippingFee = view.sellerShippingFee,
                totalProductAmount = view.totalProductAmount,
                totalShippingFee = view.totalShippingFee,
                couponDiscountAmount = view.couponDiscountAmount,
                usedPoint = view.usedPoint,
                finalPaymentAmount = view.finalPaymentAmount,
                paymentMethod = view.paymentMethod?.name,
            )
    }
}

/** 주문 시점 배송지. 수령인명·연락처는 복호한 평문이다. */
data class OrderDeliveryAddressResult(
    val receiverName: String,
    val contactNumber: String,
    val zipCode: String,
    val baseAddress: String,
    val detailAddress: String?,
    val deliveryMemo: String?,
) {
    companion object {
        fun of(
            view: OrderDeliveryAddressView,
            decryptPii: (String) -> String,
        ): OrderDeliveryAddressResult =
            OrderDeliveryAddressResult(
                receiverName = decryptPii(view.receiverNameEnc),
                contactNumber = decryptPii(view.contactNumberEnc),
                zipCode = view.zipCode,
                baseAddress = view.baseAddress,
                detailAddress = view.detailAddress,
                deliveryMemo = view.deliveryMemo,
            )
    }
}

data class OrderShipmentResult(
    val courierCode: String,
    val courierName: String,
    val trackingNumber: String,
    val dispatchedAt: LocalDateTime?,
    val deliveredAt: LocalDateTime?,
    val lastTrackingStatus: String?,
) {
    companion object {
        fun from(view: OrderShipmentView): OrderShipmentResult =
            OrderShipmentResult(
                courierCode = view.courierCode,
                courierName = view.courierName,
                trackingNumber = view.trackingNumber,
                dispatchedAt = view.dispatchedAt,
                deliveredAt = view.deliveredAt,
                lastTrackingStatus = view.lastTrackingStatus,
            )
    }
}

data class OrderActiveClaimResult(
    val claimPublicId: String,
    val claimType: ClaimType,
    val status: ClaimStatus,
    val refundStatus: RefundStatus?,
) {
    companion object {
        fun from(view: OrderActiveClaimView): OrderActiveClaimResult =
            OrderActiveClaimResult(
                claimPublicId = view.claimPublicId,
                claimType = view.claimType,
                status = view.status,
                refundStatus = view.refundStatus,
            )
    }
}
