package com.aechak.application.order.admin.usecase.result

import com.aechak.application.order.admin.port.view.AdminOrderClaimView
import com.aechak.application.order.admin.port.view.AdminOrderLineView
import com.aechak.application.order.admin.port.view.AdminOrderShipmentView
import com.aechak.application.order.admin.port.view.AdminOrderSummaryView
import com.aechak.domain.order.claim.enums.ClaimStatus
import com.aechak.domain.order.claim.enums.ClaimType
import com.aechak.domain.order.order.enums.OrderItemStatus
import com.aechak.domain.order.order.enums.OrderStatus
import java.time.LocalDateTime

/**
 * 어드민이 보는 주문 한 건의 주문 단위 출력. 목록 행과 상세가 공유한다.
 *
 * - [representativeItemName]·[itemCount]는 목록 행이 "상품명 외 N건"을 그리는 재료다.
 *   목록이 품목을 전부 받으면 페이지당 품목 수만큼 응답이 커진다 — 전문은 상세에만 싣는다
 * - [totalProductAmount]·[payableAmount]는 취소·반품으로 종결된 품목을 뺀 **주문 금액**이고
 *   취소된 주문은 0이다. 지표 카드의 금액과 같은 규칙으로 센다 — 어긋나면 카드와 목록을 비교할 수 없다.
 *   적립금은 주문그룹 단위라 셀러별 주문에 안분할 근거가 없어 빼지 않는다 — 실제 수납액이 아니다
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
            val productAmount = liveProductAmountOf(view.status, lines)
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
                payableAmount = payableAmountOf(view, productAmount),
                purchaseConfirmDueAt = view.purchaseConfirmDueAt,
                delayed = delayReason != null,
                delayReason = delayReason,
                shipment = shipment?.let(AdminOrderShipmentResult::from),
                activeClaim = claim?.let(AdminOrderClaimResult::from),
            )
        }

        /**
         * 취소·반품으로 종결된 품목을 뺀 상품금액. 취소된 주문은 0이다 — 미결제 만료 취소는 품목
         * 상태를 건드리지 않아 품목만 보면 전액이 그대로 남는다.
         */
        private fun liveProductAmountOf(
            status: OrderStatus,
            lines: List<AdminOrderLineView>,
        ): Long =
            if (status == OrderStatus.CANCELLED) {
                0L
            } else {
                lines.filterNot { it.itemStatus in OrderItemStatus.RESOLVED }.sumOf { it.unitPrice * it.quantity }
            }

        /**
         * 취소된 주문은 배송비도 쿠폰할인도 남기지 않는다. 지표 카드가 취소를 금액 모수에서 빼므로
         * 행도 같이 빼야 운영자가 카드와 목록을 비교할 수 있다 — 안 빼면 돈이 한 번도 움직이지 않은
         * 주문이 배송비만큼 금액을 가진 것처럼 보인다.
         */
        private fun payableAmountOf(
            view: AdminOrderSummaryView,
            productAmount: Long,
        ): Long =
            if (view.status == OrderStatus.CANCELLED) {
                0L
            } else {
                productAmount + view.sellerShippingFee - view.allocatedCouponDiscount
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
