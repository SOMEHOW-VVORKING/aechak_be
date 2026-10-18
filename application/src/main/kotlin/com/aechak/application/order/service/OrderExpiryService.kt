package com.aechak.application.order.service

import com.aechak.application.order.support.OrderPointLedger
import com.aechak.application.order.usecase.result.ExpireOrderGroupResult
import com.aechak.application.order.usecase.result.ExpireOrderGroupStatus
import com.aechak.application.order.usecase.result.ExpiredOrderGroupResult
import com.aechak.application.user.point.usecase.PointUseCase
import com.aechak.application.user.point.usecase.command.ReleasePointCommand
import com.aechak.domain.order.group.OrderGroup
import com.aechak.domain.order.group.enums.OrderGroupStatus
import com.aechak.domain.order.group.repository.OrderGroupRepository
import com.aechak.domain.order.order.Order
import com.aechak.domain.order.order.OrderStatusHistory
import com.aechak.domain.order.order.enums.OrderStatus
import com.aechak.domain.order.order.enums.TriggeredBy
import com.aechak.domain.order.order.repository.OrderRepository
import com.aechak.domain.order.order.repository.OrderStatusHistoryRepository
import com.aechak.domain.product.option.repository.OptionCombinationRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDateTime

/**
 * 결제되지 않은 주문그룹의 만료 처리 — Facade에서만 호출된다.
 * 주문그룹 생성이 재고와 적립금을 먼저 깎으므로, 만료를 방치하면 그만큼의 자산이 묶인 채로 남는다.
 */
@Service
class OrderExpiryService(
    private val orderGroupRepository: OrderGroupRepository,
    private val orderRepository: OrderRepository,
    private val orderStatusHistoryRepository: OrderStatusHistoryRepository,
    private val optionCombinationRepository: OptionCombinationRepository,
    private val pointUseCase: PointUseCase,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun findExpired(
        now: LocalDateTime,
        limit: Int,
    ): List<ExpiredOrderGroupResult> = orderGroupRepository.findExpiredPending(now, limit).map(ExpiredOrderGroupResult::from)

    /**
     * 취소 전이 선점에 성공한 경우에만 복원한다.
     * 선점 전에 복원하면 재실행 때 재고가 두 번 늘어난다 — 재고 증가에는 멱등키가 없어 되돌릴 근거가 남지 않는다.
     */
    fun expire(
        orderGroupId: Long,
        now: LocalDateTime,
    ): ExpireOrderGroupResult {
        val group =
            orderGroupRepository.findById(orderGroupId)
                ?: return ExpireOrderGroupResult.of(ExpireOrderGroupStatus.NOT_FOUND)
        if (group.status != OrderGroupStatus.PENDING_PAYMENT) {
            return ExpireOrderGroupResult.of(ExpireOrderGroupStatus.ALREADY_SETTLED)
        }
        if (!group.isExpired(now)) {
            return ExpireOrderGroupResult.of(ExpireOrderGroupStatus.NOT_EXPIRED)
        }
        if (!orderGroupRepository.cancelIfPending(orderGroupId)) {
            return ExpireOrderGroupResult.of(ExpireOrderGroupStatus.ALREADY_SETTLED)
        }
        // 선점 쿼리가 먼저 나가야 한다 — 애그리거트를 먼저 바꾸면 auto-flush가 상태를 써버려 자기 선점이 0행이 된다
        group.cancelUnpaid()
        val cancelled = cancelOrders(orderGroupId)
        val restored = restoreStock(group, cancelled)
        return ExpireOrderGroupResult(
            status = ExpireOrderGroupStatus.EXPIRED,
            cancelledOrders = cancelled.size,
            restoredStockItems = restored.first,
            failedStockItems = restored.second,
            releasedPoint = releasePoint(group),
        )
    }

    /** 하위 셀러 주문을 취소로 바꾸고 전이 이력을 같은 트랜잭션에 남긴다 */
    private fun cancelOrders(orderGroupId: Long): List<Order> {
        val targets =
            orderRepository
                .findAllByOrderGroupId(orderGroupId)
                .filter { it.status == OrderStatus.PENDING_PAYMENT }
        targets.forEach { order ->
            order.cancelUnpaid()
            orderStatusHistoryRepository.save(
                OrderStatusHistory.record(
                    order = order,
                    fromStatus = OrderStatus.PENDING_PAYMENT,
                    toStatus = OrderStatus.CANCELLED,
                    triggeredBy = TriggeredBy.SYSTEM,
                ),
            )
        }
        return targets
    }

    /** 옵션조합별로 합산해 한 번씩만 되돌린다. 차감과 같이 id 오름차순 — 교차 주문 간 데드락 예방 */
    private fun restoreStock(
        group: OrderGroup,
        cancelled: List<Order>,
    ): Pair<Int, Int> {
        val quantities =
            cancelled
                .flatMap { it.items }
                .groupBy { it.optionCombinationId }
                .mapValues { (_, items) -> items.sumOf { it.quantity } }
        var restored = 0
        var failed = 0
        quantities.toSortedMap().forEach { (optionCombinationId, quantity) ->
            if (optionCombinationRepository.restoreStock(optionCombinationId, quantity)) {
                restored++
            } else {
                // 복원하면 컬럼 상한을 넘기는 재고다. 되돌릴 자리가 없어 사람이 확인해야 하고, 취소 자체는 되돌리지 않는다
                failed++
                log.error(
                    "재고 복원이 상한에 걸려 건너뜀 — 사람 확인 필요. orderGroupPublicId={}, optionCombinationId={}, quantity={}",
                    group.publicId,
                    optionCombinationId,
                    quantity,
                )
            }
        }
        return restored to failed
    }

    /** 적립금을 쓰지 않은 주문도 있다 — 그때는 원장을 만들지 않는다 */
    private fun releasePoint(group: OrderGroup): Long {
        if (group.usedPoint == 0L) return 0
        pointUseCase.releasePoint(
            ReleasePointCommand(
                userId = group.buyerId,
                amount = group.usedPoint,
                idempotencyKey = OrderPointLedger.releaseKey(group.publicId),
                sourceType = OrderPointLedger.SOURCE_TYPE,
                sourceId = group.id,
            ),
        )
        return group.usedPoint
    }
}
