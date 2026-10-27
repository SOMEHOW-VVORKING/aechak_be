package com.aechak.seller.order.response

import com.aechak.application.order.seller.usecase.result.SellerOrderDetailResult
import com.aechak.application.order.seller.usecase.result.SellerOrderLineResult
import com.aechak.domain.order.order.enums.OrderItemStatus
import com.aechak.domain.order.order.enums.OrderStatus
import java.time.LocalDateTime

/** 주문 단위 필드는 목록 행과 같고 items가 더 붙는다 — 화면이 목록 행을 그대로 상세로 확장한다. */
data class SellerOrderDetailResponse(
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
    val shipment: SellerOrderShipmentResponse?,
    val activeClaim: SellerOrderClaimResponse?,
    val items: List<SellerOrderLineResponse>,
) {
    companion object {
        fun from(result: SellerOrderDetailResult): SellerOrderDetailResponse {
            val order = result.order
            return SellerOrderDetailResponse(
                orderId = order.orderId,
                orderGroupId = order.orderGroupId,
                status = order.status,
                orderedAt = order.orderedAt,
                buyerNickname = order.buyerNickname,
                recipientName = order.recipientName,
                contactNumber = order.contactNumber,
                zipCode = order.zipCode,
                baseAddress = order.baseAddress,
                detailAddress = order.detailAddress,
                deliveryMemo = order.deliveryMemo,
                itemCount = order.itemCount,
                totalProductAmount = order.totalProductAmount,
                sellerShippingFee = order.sellerShippingFee,
                allocatedCouponDiscount = order.allocatedCouponDiscount,
                payableAmount = order.payableAmount,
                purchaseConfirmDueAt = order.purchaseConfirmDueAt,
                shipment = order.shipment?.let(SellerOrderShipmentResponse::from),
                activeClaim = order.activeClaim?.let(SellerOrderClaimResponse::from),
                items = result.items.map(SellerOrderLineResponse::from),
            )
        }
    }
}

data class SellerOrderLineResponse(
    val orderItemId: Long,
    val productId: String,
    val productName: String,
    val thumbnailUrl: String?,
    val optionName: String,
    val quantity: Int,
    val unitPrice: Long,
    val discountAllocatedAmount: Long,
    val itemStatus: OrderItemStatus,
) {
    companion object {
        fun from(result: SellerOrderLineResult): SellerOrderLineResponse =
            SellerOrderLineResponse(
                orderItemId = result.orderItemId,
                productId = result.productId,
                productName = result.productName,
                thumbnailUrl = result.thumbnailUrl,
                optionName = result.optionName,
                quantity = result.quantity,
                unitPrice = result.unitPrice,
                discountAllocatedAmount = result.discountAllocatedAmount,
                itemStatus = result.itemStatus,
            )
    }
}
