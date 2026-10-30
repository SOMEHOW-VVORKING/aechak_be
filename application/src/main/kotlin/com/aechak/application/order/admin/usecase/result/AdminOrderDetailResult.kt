package com.aechak.application.order.admin.usecase.result

import com.aechak.application.order.admin.port.view.AdminOrderLineView
import com.aechak.application.order.admin.port.view.AdminOrderStatusHistoryView
import com.aechak.domain.order.nudge.OrderDispatchNudge
import com.aechak.domain.order.order.enums.ConfirmType
import com.aechak.domain.order.order.enums.OrderItemStatus
import com.aechak.domain.order.order.enums.OrderStatus
import com.aechak.domain.order.order.enums.TriggeredBy
import java.time.LocalDateTime

/**
 * 어드민 주문 상세. 목록 행과 같은 주문 단위 출력에 품목 전문·상태 전이 이력·독촉 이력을 덧붙인다.
 * 배송 진행 타임라인은 [statusHistory]가 재료다 — 단계 이름과 표시 규칙은 화면이 정한다.
 */
data class AdminOrderDetailResult(
    val order: AdminOrderSummaryResult,
    val orderGroupId: String,
    val purchaseConfirmedAt: LocalDateTime?,
    val confirmType: ConfirmType?,
    val items: List<AdminOrderLineResult>,
    val statusHistory: List<AdminOrderStatusHistoryResult>,
    val nudges: List<AdminOrderDispatchNudgeResult>,
)

data class AdminOrderLineResult(
    val orderItemId: Long,
    val productId: String,
    val productName: String,
    val optionName: String,
    val quantity: Int,
    val unitPrice: Long,
    val discountAllocatedAmount: Long,
    val itemStatus: OrderItemStatus,
) {
    companion object {
        fun from(view: AdminOrderLineView): AdminOrderLineResult =
            AdminOrderLineResult(
                orderItemId = view.orderItemId,
                productId = view.productPublicId,
                productName = view.productName,
                optionName = view.optionName,
                quantity = view.quantity,
                unitPrice = view.unitPrice,
                discountAllocatedAmount = view.discountAllocatedAmount,
                itemStatus = view.itemStatus,
            )
    }
}

data class AdminOrderStatusHistoryResult(
    val fromStatus: OrderStatus?,
    val toStatus: OrderStatus,
    val triggeredBy: TriggeredBy,
    val occurredAt: LocalDateTime,
) {
    companion object {
        fun from(view: AdminOrderStatusHistoryView): AdminOrderStatusHistoryResult =
            AdminOrderStatusHistoryResult(
                fromStatus = view.fromStatus,
                toStatus = view.toStatus,
                triggeredBy = view.triggeredBy,
                occurredAt = view.occurredAt,
            )
    }
}

/**
 * 독촉 기록 한 건. **알림 발송 여부를 담지 않는다** — 어드민 실행 모듈이 발송 어댑터를 조립하지
 * 않아 이 경로는 기록만 남긴다. 보내지도 않은 것을 보냈다고 말하는 필드를 두지 않는다.
 */
data class AdminOrderDispatchNudgeResult(
    val nudgeId: Long,
    val adminUserId: Long,
    val memo: String?,
    val nudgedAt: LocalDateTime,
) {
    companion object {
        fun from(nudge: OrderDispatchNudge): AdminOrderDispatchNudgeResult =
            AdminOrderDispatchNudgeResult(
                nudgeId = nudge.id,
                adminUserId = nudge.adminUserId,
                memo = nudge.memo,
                nudgedAt = nudge.createdAt,
            )
    }
}
