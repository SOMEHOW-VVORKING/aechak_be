package com.aechak.application.order.service

import com.aechak.application.order.usecase.command.ConfirmPurchaseCommand
import com.aechak.common.error.BusinessException
import com.aechak.domain.order.claim.repository.ClaimRepository
import com.aechak.domain.order.error.OrderErrorCode
import com.aechak.domain.order.order.OrderStatusHistory
import com.aechak.domain.order.order.enums.ConfirmType
import com.aechak.domain.order.order.enums.OrderStatus
import com.aechak.domain.order.order.enums.TriggeredBy
import com.aechak.domain.order.order.repository.OrderRepository
import com.aechak.domain.order.order.repository.OrderStatusHistoryRepository
import org.springframework.stereotype.Service
import java.time.LocalDate

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

        order.confirmPurchase(ConfirmType.MANUAL)
        orderStatusHistoryRepository.save(
            OrderStatusHistory.record(
                order = order,
                fromStatus = OrderStatus.DELIVERED,
                toStatus = OrderStatus.PURCHASE_CONFIRMED,
                triggeredBy = TriggeredBy.BUYER,
            ),
        )

        return ConfirmedPurchase(
            orderId = order.id,
            sellerId = order.sellerId,
            productAmount = order.productAmountAfterDiscount(),
            sellerShippingFee = order.sellerShippingFee,
            // 컨테이너 TZ가 Asia/Seoul이라 확정 시각의 날짜가 그대로 정산 달력의 기준일이다
            confirmedOn = order.purchaseConfirmedAt!!.toLocalDate(),
            items = order.items.map { ConfirmedItem(orderItemId = it.id, grossAmount = it.productAmountAfterDiscount()) },
        )
    }

    /** 적립·정산 기준을 넘기기 위한 확정 결과 — UseCase 경계를 넘지 않는다. */
    data class ConfirmedPurchase(
        val orderId: Long,
        val sellerId: Long,
        val productAmount: Long,
        val sellerShippingFee: Long,
        val confirmedOn: LocalDate,
        val items: List<ConfirmedItem>,
    )

    /** 정산 원장이 품목당 1행이라 품목 단위 상품금액까지 함께 넘긴다. */
    data class ConfirmedItem(
        val orderItemId: Long,
        val grossAmount: Long,
    )
}
