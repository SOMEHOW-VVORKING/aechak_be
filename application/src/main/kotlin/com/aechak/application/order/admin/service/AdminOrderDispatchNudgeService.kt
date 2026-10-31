package com.aechak.application.order.admin.service

import com.aechak.application.order.admin.usecase.command.NudgeOrderDispatchCommand
import com.aechak.common.error.BusinessException
import com.aechak.domain.order.error.OrderErrorCode
import com.aechak.domain.order.nudge.OrderDispatchNudge
import com.aechak.domain.order.nudge.repository.OrderDispatchNudgeRepository
import com.aechak.domain.order.order.repository.OrderRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.LocalDateTime

/**
 * 셀러 발송 독촉 기록.
 *
 * 같은 주문에 반복될 수 있는 행위라 쿨다운(`order.dispatch.nudge-cooldown-hours`)으로 묶는다 —
 * 셀러가 반응할 시간을 주지 않고 쌓으면 이력이 "운영자가 연타했다"는 기록이 되고, 발송 알림이 붙는
 * 시점에 그 행 수만큼 셀러에게 나간다.
 */
@Service
class AdminOrderDispatchNudgeService(
    private val orderRepository: OrderRepository,
    private val nudgeRepository: OrderDispatchNudgeRepository,
    @param:Value("\${order.dispatch.nudge-cooldown-hours:6}") private val cooldownHours: Long,
) {
    fun nudge(
        command: NudgeOrderDispatchCommand,
        at: LocalDateTime,
    ): OrderDispatchNudge {
        // 주문 행을 선점해 같은 주문에 겹친 독촉을 직렬화한다 — 선점 없이 쿨다운을 읽으면
        // 동시에 들어온 두 요청이 모두 "직전 독촉 없음"을 보고 둘 다 기록된다
        val order =
            orderRepository.findByPublicIdForUpdate(command.orderPublicId)
                ?: throw BusinessException(OrderErrorCode.ORDER_NOT_FOUND)

        if (!order.status.isAwaitingDispatch()) {
            throw BusinessException(OrderErrorCode.ORDER_NUDGE_NOT_APPLICABLE)
        }
        val cooldownStart = at.minusHours(cooldownHours)
        val latest = nudgeRepository.findLatestByOrderId(order.id)
        if (latest != null && !latest.createdAt.isBefore(cooldownStart)) {
            throw BusinessException(OrderErrorCode.ORDER_NUDGE_TOO_SOON)
        }

        return nudgeRepository.save(command.toEntity(order.id))
    }

    fun getHistory(orderId: Long): List<OrderDispatchNudge> = nudgeRepository.findAllByOrderIdDesc(orderId)
}
