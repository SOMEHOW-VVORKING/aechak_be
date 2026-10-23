package com.aechak.application.order.delivery.service

import com.aechak.common.error.BusinessException
import com.aechak.domain.order.error.OrderErrorCode
import com.aechak.domain.order.order.Order
import com.aechak.domain.order.order.OrderStatusHistory
import com.aechak.domain.order.order.enums.OrderStatus
import com.aechak.domain.order.order.enums.TriggeredBy
import com.aechak.domain.order.order.repository.OrderRepository
import com.aechak.domain.order.order.repository.OrderStatusHistoryRepository
import com.aechak.domain.order.shipment.Shipment
import com.aechak.domain.order.shipment.enums.ShipmentType
import com.aechak.domain.order.shipment.repository.ShipmentRepository
import org.springframework.stereotype.Service
import java.time.LocalDateTime

/** 배송완료 전이 — 셀러가 직접 누르는 경로와 배치가 시간으로 넘기는 경로가 함께 쓴다. */
@Service
class OrderDeliveryService(
    private val orderRepository: OrderRepository,
    private val orderStatusHistoryRepository: OrderStatusHistoryRepository,
    private val shipmentRepository: ShipmentRepository,
    private val autoConfirmPolicy: AutoConfirmPolicy,
) {
    fun findPendingDeliveries(
        dispatchedBefore: LocalDateTime,
        afterShipmentId: Long,
        limit: Int,
    ): List<Shipment> = shipmentRepository.findPendingDispatchedBefore(dispatchedBefore, afterShipmentId, limit)

    fun findTrackingTargets(
        afterShipmentId: Long,
        limit: Int,
    ): List<Shipment> = shipmentRepository.findTrackingTargets(afterShipmentId, limit)

    /**
     * 택배사가 준 최근 상태를 발송에 기록하고, 실제로 담긴 문구를 돌려준다. 상태 전이는 일으키지 않는다.
     * 행을 잠그고 읽는다 — 더티체킹이 행 전체를 다시 쓰므로 잠그지 않으면 그 사이 커밋된 배송완료 시각이 지워진다.
     */
    fun applyTracking(
        shipmentId: Long,
        lastStatus: String,
    ): String {
        val shipment =
            shipmentRepository.findForUpdateById(shipmentId)
                ?: throw BusinessException(OrderErrorCode.SHIPMENT_NOT_FOUND)
        shipment.syncTracking(lastStatus)
        return requireNotNull(shipment.lastTrackingStatus)
    }

    fun completeBySystem(
        orderId: Long,
        deliveredAt: LocalDateTime,
    ) {
        val order = orderRepository.findById(orderId) ?: throw BusinessException(OrderErrorCode.ORDER_NOT_FOUND)
        complete(order, deliveredAt, TriggeredBy.SYSTEM)
    }

    /** 배송완료로 전이하고 구매확정 기한을 부여한다. 부여한 기한을 돌려준다. */
    fun complete(
        order: Order,
        deliveredAt: LocalDateTime,
        triggeredBy: TriggeredBy,
    ): LocalDateTime {
        val shipment =
            shipmentRepository.findByOrderIdAndShipmentType(order.id, ShipmentType.INITIAL)
                ?: throw BusinessException(OrderErrorCode.SHIPMENT_NOT_FOUND)
        if (shipment.deliveredAt != null) {
            throw BusinessException(OrderErrorCode.SHIPMENT_ALREADY_DELIVERED)
        }

        // 전이의 심판은 조건부 UPDATE다. 애그리거트를 건드리기 전에 선점한다 — 더티체킹 플러시가 앞지르면 0행이 된다
        if (!orderRepository.transitionStatusIf(order.id, OrderStatus.SHIPPING, OrderStatus.DELIVERED)) {
            throw BusinessException(OrderErrorCode.SHIPMENT_ALREADY_DELIVERED)
        }

        shipment.markDelivered(deliveredAt)
        val autoConfirmDueAt = autoConfirmPolicy.dueAt(deliveredAt)
        order.markDelivered(autoConfirmDueAt)
        orderStatusHistoryRepository.save(
            OrderStatusHistory.record(
                order = order,
                fromStatus = OrderStatus.SHIPPING,
                toStatus = OrderStatus.DELIVERED,
                triggeredBy = triggeredBy,
            ),
        )
        return autoConfirmDueAt
    }
}
