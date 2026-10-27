package com.aechak.application.order.seller.usecase.result

import com.aechak.application.order.seller.port.view.SellerOrderClaimView
import com.aechak.application.order.seller.port.view.SellerOrderShipmentView
import com.aechak.application.order.seller.port.view.SellerOrderSummaryView
import com.aechak.domain.order.claim.enums.ClaimStatus
import com.aechak.domain.order.claim.enums.ClaimType
import com.aechak.domain.order.order.enums.OrderStatus
import java.time.LocalDateTime

/**
 * 셀러 주문 한 건의 주문 단위 출력. 목록 행과 상세가 공유한다.
 * 수령인명·연락처는 평문으로 내리고 마스킹은 화면이 한다 — 심사·회원 조회와 같은 결.
 */
data class SellerOrderSummaryResult(
    val orderId: String,
    val orderGroupId: String,
    val status: OrderStatus,
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
    val shipment: SellerOrderShipmentResult?,
    val activeClaim: SellerOrderClaimResult?,
) {
    companion object {
        fun of(
            view: SellerOrderSummaryView,
            buyerNickname: String,
            shipment: SellerOrderShipmentView?,
            claim: SellerOrderClaimView?,
            decryptPii: (String) -> String,
        ): SellerOrderSummaryResult =
            SellerOrderSummaryResult(
                orderId = view.orderPublicId,
                orderGroupId = view.orderGroupPublicId,
                status = view.status,
                orderedAt = view.orderedAt,
                buyerNickname = buyerNickname,
                recipientName = decryptPii(view.receiverNameEnc),
                contactNumber = decryptPii(view.contactNumberEnc),
                zipCode = view.zipCode,
                baseAddress = view.baseAddress,
                detailAddress = view.detailAddress,
                deliveryMemo = view.deliveryMemo,
                itemCount = view.itemCount,
                totalProductAmount = view.totalProductAmount,
                sellerShippingFee = view.sellerShippingFee,
                allocatedCouponDiscount = view.allocatedCouponDiscount,
                payableAmount = view.totalProductAmount + view.sellerShippingFee - view.allocatedCouponDiscount,
                purchaseConfirmDueAt = view.purchaseConfirmDueAt,
                shipment = shipment?.let(SellerOrderShipmentResult::from),
                activeClaim = claim?.let(SellerOrderClaimResult::from),
            )
    }
}

data class SellerOrderShipmentResult(
    val courierCode: String,
    val courierName: String,
    val trackingNumber: String,
    val dispatchedAt: LocalDateTime?,
    val deliveredAt: LocalDateTime?,
    val lastTrackingStatus: String?,
) {
    companion object {
        fun from(view: SellerOrderShipmentView): SellerOrderShipmentResult =
            SellerOrderShipmentResult(
                courierCode = view.courierCode,
                courierName = view.courierName,
                trackingNumber = view.trackingNumber,
                dispatchedAt = view.dispatchedAt,
                deliveredAt = view.deliveredAt,
                lastTrackingStatus = view.lastTrackingStatus,
            )
    }
}

data class SellerOrderClaimResult(
    val claimId: String,
    val claimType: ClaimType,
    val status: ClaimStatus,
) {
    companion object {
        fun from(view: SellerOrderClaimView): SellerOrderClaimResult =
            SellerOrderClaimResult(
                claimId = view.claimPublicId,
                claimType = view.claimType,
                status = view.status,
            )
    }
}
