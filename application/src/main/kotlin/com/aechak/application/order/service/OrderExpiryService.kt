package com.aechak.application.order.service

import com.aechak.application.order.support.OrderPointLedger
import com.aechak.application.order.usecase.query.ExpiredOrderGroupQuery
import com.aechak.application.order.usecase.result.ExpireOrderGroupResult
import com.aechak.application.order.usecase.result.ExpireOrderGroupStatus
import com.aechak.application.order.usecase.result.ExpiredOrderGroupResult
import com.aechak.application.product.option.usecase.OptionStockUseCase
import com.aechak.application.product.option.usecase.command.RestoreOptionStockCommand
import com.aechak.application.product.option.usecase.result.RestoreOptionStockStatus
import com.aechak.application.user.point.usecase.PointUseCase
import com.aechak.application.user.point.usecase.command.ReleasePointCommand
import com.aechak.domain.order.group.OrderGroup
import com.aechak.domain.order.group.enums.OrderGroupStatus
import com.aechak.domain.order.group.repository.OrderGroupRepository
import com.aechak.domain.order.order.Order
import com.aechak.domain.order.order.OrderItem
import com.aechak.domain.order.order.OrderStatusHistory
import com.aechak.domain.order.order.enums.OrderItemStatus
import com.aechak.domain.order.order.enums.OrderStatus
import com.aechak.domain.order.order.enums.TriggeredBy
import com.aechak.domain.order.order.repository.OrderRepository
import com.aechak.domain.order.order.repository.OrderStatusHistoryRepository
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
    private val optionStockUseCase: OptionStockUseCase,
    private val pointUseCase: PointUseCase,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun findExpired(
        now: LocalDateTime,
        query: ExpiredOrderGroupQuery,
    ): List<ExpiredOrderGroupResult> =
        orderGroupRepository
            .findExpiredPending(
                now = now,
                afterExpiresAt = query.afterExpiresAt,
                afterId = query.afterOrderGroupId,
                limit = query.limit,
            ).map(ExpiredOrderGroupResult::from)

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
        return ExpireOrderGroupResult(
            status = ExpireOrderGroupStatus.EXPIRED,
            cancelledOrders = cancelled.orders.size,
            skippedStockItems = restoreStock(group, cancelled.items),
            releasedPoint = releasePoint(group),
        )
    }

    /** 하위 셀러 주문과 그 품목을 취소로 바꾸고 전이 이력을 같은 트랜잭션에 남긴다 */
    private fun cancelOrders(orderGroupId: Long): CancelledOrders {
        val targets =
            orderRepository
                .findAllByOrderGroupId(orderGroupId)
                .filter { it.status == OrderStatus.PENDING_PAYMENT }
        val items = mutableListOf<OrderItem>()
        targets.forEach { order ->
            order.cancelUnpaid()
            // 주문됨 상태인 품목만 내린다
            order.items.filter { it.itemStatus == OrderItemStatus.ORDERED }.forEach {
                it.markCancelled()
                items += it
            }
            orderStatusHistoryRepository.save(
                OrderStatusHistory.record(
                    order = order,
                    fromStatus = OrderStatus.PENDING_PAYMENT,
                    toStatus = OrderStatus.CANCELLED,
                    triggeredBy = TriggeredBy.SYSTEM,
                ),
            )
        }
        return CancelledOrders(targets, items)
    }

    /**
     * 옵션조합별로 합산해 한 번씩만 되돌린다. 차감과 같이 id 오름차순 — 교차 주문 간 데드락 예방.
     *
     * 되돌릴 자리가 생길 수 있는 실패(상한 초과·원인 불명)는 던져서 만료 전체를 롤백한다 —
     * 결제대기로 남겨 두는 것이 복원 의무를 보존하는 유일한 방법이고, 다음 회차가 다시 시도한다.
     * 옵션조합이 사라진 경우만 건너뛴다. 되돌릴 대상 자체가 없어 재시도해도 달라지지 않는다.
     */
    private fun restoreStock(
        group: OrderGroup,
        cancelledItems: List<OrderItem>,
    ): Int {
        val quantities =
            cancelledItems
                .groupBy { it.optionCombinationId }
                .mapValues { (_, items) -> items.sumOf { it.quantity } }
        val retryable = mutableListOf<String>()
        var skipped = 0
        quantities.toSortedMap().forEach { (optionCombinationId, quantity) ->
            when (optionStockUseCase.restoreStock(RestoreOptionStockCommand(optionCombinationId, quantity)).status) {
                RestoreOptionStockStatus.RESTORED -> {
                    Unit
                }

                RestoreOptionStockStatus.OPTION_NOT_FOUND -> {
                    skipped++
                    log.error(
                        "복원 대상 옵션조합이 없어 재고를 건너뜀. orderGroupPublicId={}, optionCombinationId={}, quantity={}",
                        group.publicId,
                        optionCombinationId,
                        quantity,
                    )
                }

                RestoreOptionStockStatus.STOCK_LIMIT_EXCEEDED,
                RestoreOptionStockStatus.UNKNOWN,
                -> {
                    retryable += "옵션조합 $optionCombinationId 수량 $quantity"
                }
            }
        }
        if (retryable.isNotEmpty()) {
            error("재고 복원 실패로 만료를 보류함 (orderGroupPublicId=${group.publicId}, 미복원=${retryable.joinToString()})")
        }
        return skipped
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

    private class CancelledOrders(
        val orders: List<Order>,
        val items: List<OrderItem>,
    )
}
