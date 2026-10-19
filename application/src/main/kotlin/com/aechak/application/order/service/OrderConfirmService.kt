package com.aechak.application.order.service

import com.aechak.application.order.usecase.command.ConfirmPurchaseCommand
import com.aechak.application.order.usecase.query.AutoConfirmScanQuery
import com.aechak.application.order.usecase.query.ConfirmNotifyScanQuery
import com.aechak.common.error.BusinessException
import com.aechak.domain.order.claim.repository.ClaimRepository
import com.aechak.domain.order.error.OrderErrorCode
import com.aechak.domain.order.order.Order
import com.aechak.domain.order.order.OrderStatusHistory
import com.aechak.domain.order.order.enums.ConfirmType
import com.aechak.domain.order.order.enums.OrderStatus
import com.aechak.domain.order.order.enums.TriggeredBy
import com.aechak.domain.order.order.repository.OrderRepository
import com.aechak.domain.order.order.repository.OrderStatusHistoryRepository
import org.springframework.stereotype.Service
import java.time.LocalDateTime

/**
 * 구매확정 로직 보관함 — Facade에서만 호출된다.
 * 확정은 비가역이고 반품 기한·정산의 기산점이라, 전이 선점과 클레임 확인을 모두 통과한 건만 확정으로 넘긴다.
 */
@Service
class OrderConfirmService(
    private val orderRepository: OrderRepository,
    private val orderStatusHistoryRepository: OrderStatusHistoryRepository,
    private val claimRepository: ClaimRepository,
) {
    fun confirmPurchase(command: ConfirmPurchaseCommand): ConfirmedPurchase {
        val order =
            orderRepository.findByPublicIdAndBuyerId(command.orderPublicId, command.buyerId)
                ?: throw BusinessException(OrderErrorCode.ORDER_NOT_FOUND)

        return confirm(order, ConfirmType.MANUAL, TriggeredBy.BUYER)
    }

    /** 기한이 지난 건의 자동확정 — 구매자가 누른 확정과 같은 전이·선점·클레임 판정을 탄다. */
    fun autoConfirmPurchase(orderId: Long): ConfirmedPurchase {
        val order = orderRepository.findById(orderId) ?: throw BusinessException(OrderErrorCode.ORDER_NOT_FOUND)

        return confirm(order, ConfirmType.AUTO, TriggeredBy.SYSTEM)
    }

    fun findAutoConfirmTargetIds(query: AutoConfirmScanQuery): List<Long> =
        orderRepository.findAutoConfirmDueIds(query.dueAtOrBefore, query.afterOrderId, query.limit)

    fun findConfirmNotifyTargetIds(query: ConfirmNotifyScanQuery): List<Long> =
        orderRepository.findConfirmNotifyTargetIds(query.dueAtAfter, query.dueAtOrBefore, query.afterOrderId, query.limit)

    /** 사전 안내 기록 — 알림 발송 경로가 붙기 전까지 안내 시각만 남긴다. */
    fun markConfirmNotified(
        orderId: Long,
        notifiedAt: LocalDateTime,
    ) {
        val order = orderRepository.findById(orderId) ?: throw BusinessException(OrderErrorCode.ORDER_NOT_FOUND)
        order.markConfirmNotified(notifiedAt)
    }

    private fun confirm(
        order: Order,
        confirmType: ConfirmType,
        triggeredBy: TriggeredBy,
    ): ConfirmedPurchase {
        if (order.status == OrderStatus.PURCHASE_CONFIRMED) {
            throw BusinessException(OrderErrorCode.ORDER_ALREADY_CONFIRMED)
        }
        if (order.status != OrderStatus.DELIVERED) {
            throw BusinessException(OrderErrorCode.ORDER_NOT_CONFIRMABLE)
        }

        // 전이의 심판은 조건부 UPDATE다. 배송완료에서 나가는 전이는 구매확정뿐이라 경합에서 진 쪽은 이미 확정된 건이다
        if (!orderRepository.transitionStatusIf(order.id, OrderStatus.DELIVERED, OrderStatus.PURCHASE_CONFIRMED)) {
            throw BusinessException(OrderErrorCode.ORDER_ALREADY_CONFIRMED)
        }
        // 선점이 주문 행 락을 쥔 뒤에 본다 — 먼저 보면 그 사이 접수된 클레임을 놓친다
        if (claimRepository.existsInProgressByOrderId(order.id)) {
            throw BusinessException(OrderErrorCode.ORDER_NOT_CONFIRMABLE)
        }

        order.confirmPurchase(confirmType)
        orderStatusHistoryRepository.save(
            OrderStatusHistory.record(
                order = order,
                fromStatus = OrderStatus.DELIVERED,
                toStatus = OrderStatus.PURCHASE_CONFIRMED,
                triggeredBy = triggeredBy,
            ),
        )

        return ConfirmedPurchase(
            orderId = order.id,
            buyerId = order.orderGroup.buyerId,
            orderPublicId = order.publicId,
            productAmount = order.productAmountAfterDiscount(),
        )
    }

    /** 적립 기준을 넘기기 위한 확정 결과 — UseCase 경계를 넘지 않는다. */
    data class ConfirmedPurchase(
        val orderId: Long,
        val buyerId: Long,
        val orderPublicId: String,
        val productAmount: Long,
    )
}
