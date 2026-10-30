package com.aechak.application.order.admin.usecase.result

import com.aechak.application.order.admin.port.view.AdminOrderClaimView
import com.aechak.application.order.admin.port.view.AdminOrderLineView
import com.aechak.application.order.admin.port.view.AdminOrderShipmentView
import com.aechak.application.order.admin.port.view.AdminOrderSummaryView
import com.aechak.domain.order.claim.enums.ClaimStatus
import com.aechak.domain.order.claim.enums.ClaimType
import com.aechak.domain.order.order.enums.OrderStatus
import java.time.LocalDateTime

/**
 * 어드민이 보는 주문 한 건의 주문 단위 출력. 목록 행과 상세가 공유한다.
 *
 * - [representativeItemName]·[itemCount]는 목록 행이 "상품명 외 N건"을 그리는 재료다.
 *   목록이 품목을 전부 받으면 페이지당 품목 수만큼 응답이 커진다 — 전문은 상세에만 싣는다
 * - [delayReason]이 null이면 정상 흐름이다. 판정 기준은 서버가 쥔다
 * - 수령인·연락처·주소는 담지 않는다 — 배송 진행 감시에 쓰이지 않는 개인정보다
 */
data class AdminOrderSummaryResult(
    val orderId: String,
    val status: OrderStatus,
    val orderedAt: LocalDateTime,
    val sellerId: Long,
    val sellerName: String?,
    val buyerNickname: String,
    val representativeItemName: String?,
    val itemCount: Int,
    val totalProductAmount: Long,
    val sellerShippingFee: Long,
    val allocatedCouponDiscount: Long,
    val payableAmount: Long,
    val purchaseConfirmDueAt: LocalDateTime?,
    val delayed: Boolean,
    val delayReason: AdminOrderDelayReason?,
    val shipment: AdminOrderShipmentResult?,
    val activeClaim: AdminOrderClaimResult?,
) {
    companion object {
        fun of(
            view: AdminOrderSummaryView,
            buyerNickname: String,
            lines: List<AdminOrderLineView>,
            shipment: AdminOrderShipmentView?,
            claim: AdminOrderClaimView?,
            delayReason: AdminOrderDelayReason?,
        ): AdminOrderSummaryResult {
            val productAmount = lines.sumOf { it.unitPrice * it.quantity }
            return AdminOrderSummaryResult(
                orderId = view.orderPublicId,
                status = view.status,
                orderedAt = view.orderedAt,
                sellerId = view.sellerId,
                sellerName = view.sellerName,
                buyerNickname = buyerNickname,
                representativeItemName = lines.firstOrNull()?.productName,
                itemCount = lines.size,
                totalProductAmount = productAmount,
                sellerShippingFee = view.sellerShippingFee,
                allocatedCouponDiscount = view.allocatedCouponDiscount,
                payableAmount = productAmount + view.sellerShippingFee - view.allocatedCouponDiscount,
                purchaseConfirmDueAt = view.purchaseConfirmDueAt,
                delayed = delayReason != null,
                delayReason = delayReason,
                shipment = shipment?.let(AdminOrderShipmentResult::from),
                activeClaim = claim?.let(AdminOrderClaimResult::from),
            )
        }
    }
}

data class AdminOrderShipmentResult(
    val courierCode: String,
    val courierName: String,
    val trackingNumber: String,
    val dispatchedAt: LocalDateTime?,
    val deliveredAt: LocalDateTime?,
    val lastTrackingStatus: String?,
) {
    companion object {
        fun from(view: AdminOrderShipmentView): AdminOrderShipmentResult =
            AdminOrderShipmentResult(
                courierCode = view.courierCode,
                courierName = view.courierName,
                trackingNumber = view.trackingNumber,
                dispatchedAt = view.dispatchedAt,
                deliveredAt = view.deliveredAt,
                lastTrackingStatus = view.lastTrackingStatus,
            )
    }
}

data class AdminOrderClaimResult(
    val claimId: String,
    val claimType: ClaimType,
    val status: ClaimStatus,
) {
    companion object {
        fun from(view: AdminOrderClaimView): AdminOrderClaimResult =
            AdminOrderClaimResult(
                claimId = view.claimPublicId,
                claimType = view.claimType,
                status = view.status,
            )
    }
}
