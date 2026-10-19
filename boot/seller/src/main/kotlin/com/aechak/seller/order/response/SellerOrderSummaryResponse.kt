package com.aechak.seller.order.response

import com.aechak.application.order.seller.usecase.result.SellerOrderClaimResult
import com.aechak.application.order.seller.usecase.result.SellerOrderShipmentResult
import com.aechak.application.order.seller.usecase.result.SellerOrderSummaryResult
import java.time.LocalDateTime

/** 수령인명·연락처는 평문이다 — 마스킹은 화면이 한다. */
data class SellerOrderSummaryResponse(
    val orderId: String,
    val orderGroupId: String,
    val status: String,
    val orderedAt: LocalDateTime,
    val buyerNickname: String,
    val recipientName: String,
    val contactNumber: String,
    val zipCode: String,
    val baseAddress: String,
    val detailAddress: String?,
    val deliveryMemo: String?,
    val itemCount: Long,
    val totalProductAmount: Long,
    val sellerShippingFee: Long,
    val allocatedCouponDiscount: Long,
    val payableAmount: Long,
    val purchaseConfirmDueAt: LocalDateTime?,
    val shipment: SellerOrderShipmentResponse?,
    val activeClaim: SellerOrderClaimResponse?,
) {
    companion object {
        fun from(result: SellerOrderSummaryResult): SellerOrderSummaryResponse =
            SellerOrderSummaryResponse(
                orderId = result.orderId,
                orderGroupId = result.orderGroupId,
                status = result.status,
                orderedAt = result.orderedAt,
                buyerNickname = result.buyerNickname,
                recipientName = result.recipientName,
                contactNumber = result.contactNumber,
                zipCode = result.zipCode,
                baseAddress = result.baseAddress,
                detailAddress = result.detailAddress,
                deliveryMemo = result.deliveryMemo,
                itemCount = result.itemCount,
                totalProductAmount = result.totalProductAmount,
                sellerShippingFee = result.sellerShippingFee,
                allocatedCouponDiscount = result.allocatedCouponDiscount,
                payableAmount = result.payableAmount,
                purchaseConfirmDueAt = result.purchaseConfirmDueAt,
                shipment = result.shipment?.let(SellerOrderShipmentResponse::from),
                activeClaim = result.activeClaim?.let(SellerOrderClaimResponse::from),
            )
    }
}

data class SellerOrderShipmentResponse(
    val courierCode: String,
    val courierName: String,
    val trackingNumber: String,
    val dispatchedAt: LocalDateTime?,
    val deliveredAt: LocalDateTime?,
    val lastTrackingStatus: String?,
) {
    companion object {
        fun from(result: SellerOrderShipmentResult): SellerOrderShipmentResponse =
            SellerOrderShipmentResponse(
                courierCode = result.courierCode,
                courierName = result.courierName,
                trackingNumber = result.trackingNumber,
                dispatchedAt = result.dispatchedAt,
                deliveredAt = result.deliveredAt,
                lastTrackingStatus = result.lastTrackingStatus,
            )
    }
}

data class SellerOrderClaimResponse(
    val claimId: String,
    val claimType: String,
    val status: String,
) {
    companion object {
        fun from(result: SellerOrderClaimResult): SellerOrderClaimResponse =
            SellerOrderClaimResponse(
                claimId = result.claimId,
                claimType = result.claimType,
                status = result.status,
            )
    }
}
