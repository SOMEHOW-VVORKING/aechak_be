package com.aechak.application.order.claim.service

import com.aechak.application.order.claim.usecase.result.ExecuteClaimRefundResult
import com.aechak.application.order.claim.usecase.result.ExecuteClaimRefundStatus
import com.aechak.application.order.claim.usecase.result.RefundPendingClaimResult
import com.aechak.application.order.support.OrderPointLedger
import com.aechak.application.payment.usecase.PaymentUseCase
import com.aechak.application.payment.usecase.command.ApplyPaymentCancelCommand
import com.aechak.application.product.option.usecase.OptionStockUseCase
import com.aechak.application.product.option.usecase.command.RestoreOptionStockCommand
import com.aechak.application.product.option.usecase.result.RestoreOptionStockStatus
import com.aechak.application.user.point.usecase.PointUseCase
import com.aechak.application.user.point.usecase.command.ReleasePointCommand
import com.aechak.common.error.BusinessException
import com.aechak.domain.order.claim.Claim
import com.aechak.domain.order.claim.enums.ClaimType
import com.aechak.domain.order.claim.repository.ClaimRepository
import com.aechak.domain.order.error.OrderErrorCode
import com.aechak.domain.order.group.OrderGroup
import com.aechak.domain.order.group.enums.OrderGroupStatus
import com.aechak.domain.order.group.repository.OrderGroupRepository
import com.aechak.domain.order.order.Order
import com.aechak.domain.order.order.OrderStatusHistory
import com.aechak.domain.order.order.enums.OrderItemStatus
import com.aechak.domain.order.order.enums.OrderStatus
import com.aechak.domain.order.order.enums.TriggeredBy
import com.aechak.domain.order.order.repository.OrderRepository
import com.aechak.domain.order.order.repository.OrderStatusHistoryRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * 클레임 환불 집행의 두 트랜잭션 본체 — Facade에서만 호출된다.
 * 선점(reserve)과 반영(settle) 사이에 PG 호출이 들어가므로 둘은 반드시 다른 트랜잭션이다.
 */
@Service
class ClaimRefundService(
    private val claimRepository: ClaimRepository,
    private val orderRepository: OrderRepository,
    private val orderGroupRepository: OrderGroupRepository,
    private val orderStatusHistoryRepository: OrderStatusHistoryRepository,
    private val claimAmountCalculator: ClaimAmountCalculator,
    private val paymentUseCase: PaymentUseCase,
    private val optionStockUseCase: OptionStockUseCase,
    private val pointUseCase: PointUseCase,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun findRefundPending(limit: Int): List<RefundPendingClaimResult> =
        claimRepository.findRefundPending(limit).map(RefundPendingClaimResult::from)

    /**
     * 환불 요청 상태 선점. 이 1행 커밋이 집행의 유일한 멱등 게이트다 —
     * 성공한 호출만 PG를 부르고 자산을 되돌린다. 대상이 아니면 null.
     */
    fun reserve(claimPublicId: String): RefundReservation? {
        val claim = loadClaim(claimPublicId)
        if (claim.claimType != ClaimType.CANCEL) {
            // 반품·교환은 회수를 거친 뒤 집행한다 — 이 경로로 들어오면 잘못 스캔된 것이다
            throw BusinessException(OrderErrorCode.INVALID_CLAIM_STATUS_TRANSITION)
        }
        if (!claim.isRefundExecutable()) return null

        val order = loadOrder(claim)
        val amounts = refundAmounts(claim, order)
        claim.markRefundRequested()
        if (amounts.refundAmount <= 0L) {
            // 전량이 적립금으로 덮인 클레임 — PG에 0원 취소를 보낼 수 없어 자동 집행을 멈추고 운영자에게 넘긴다
            claim.markRefundFailed()
            claimRepository.save(claim)
            log.error("환불액이 0원이라 집행하지 않음 — 운영자 확인 필요. claimPublicId={}", claimPublicId)
            return null
        }
        claimRepository.save(claim)
        return RefundReservation(
            claimPublicId = claim.publicId,
            orderGroupId = order.orderGroup.id,
            refundAmount = amounts.refundAmount,
        )
    }

    /** PG 실패를 클레임에 남긴다 — 재집행은 운영자 판단이고 스캔 대상에서 빠진다. */
    fun markFailed(claimPublicId: String) {
        val claim = loadClaim(claimPublicId)
        claim.markRefundFailed()
        claimRepository.save(claim)
    }

    /**
     * PG가 확정한 금액을 원장에서 빼고, 같은 트랜잭션에서 주문그룹·주문·품목을 전이시키고 자산을 되돌린다.
     * 원장 차감과 주문그룹 전이가 갈리면 대사가 그 결제를 미동기화로 판정해 사람을 부른다.
     */
    fun settle(
        claimPublicId: String,
        cancelledAmount: Long,
    ): ExecuteClaimRefundResult {
        val claim = loadClaim(claimPublicId)
        val order = loadOrder(claim)
        val group =
            orderGroupRepository.findById(order.orderGroup.id)
                ?: throw BusinessException(OrderErrorCode.ORDER_NOT_FOUND)
        val amounts = refundAmounts(claim, order)

        val remainingCancellable =
            paymentUseCase
                .applyCancelledAmount(
                    ApplyPaymentCancelCommand(group.id, cancelledAmount),
                ).cancellableAmount
        transitionGroup(group, fullyCancelled = remainingCancellable == 0L)
        cancelClaimedItems(claim, order)

        val result =
            ExecuteClaimRefundResult(
                status = ExecuteClaimRefundStatus.REFUNDED,
                refundAmount = cancelledAmount,
                releasedPoint = releasePoint(claim, group, amounts.pointRefund),
                skippedStockItems = restoreStock(claim),
            )
        claim.markRefunded()
        claimRepository.save(claim)
        return result
    }

    /**
     * 주문그룹 전이. 선점 쿼리가 애그리거트 변경보다 먼저 나가야 한다 —
     * 애그리거트를 먼저 바꾸면 auto-flush가 상태를 써버려 자기 선점이 0행이 된다.
     */
    private fun transitionGroup(
        group: OrderGroup,
        fullyCancelled: Boolean,
    ) {
        val next = if (fullyCancelled) OrderGroupStatus.CANCELLED else OrderGroupStatus.PARTIALLY_CANCELLED
        if (!orderGroupRepository.transitionStatusIf(group.id, group.status, next)) {
            throw BusinessException(OrderErrorCode.ORDER_GROUP_NOT_CANCELLABLE)
        }
        group.applyClaimCancellation(fullyCancelled)
    }

    /** 대상 품목을 취소하고, 그 결과 주문의 모든 품목이 취소됐으면 주문까지 전이시킨다. */
    private fun cancelClaimedItems(
        claim: Claim,
        order: Order,
    ) {
        val targets = claim.claimedQuantityByOrderItemId()
        order.items.filter { it.id in targets }.forEach { it.markCancelled() }
        if (order.items.any { it.itemStatus != OrderItemStatus.CANCELLED }) return
        val from = order.status
        order.cancelByClaimApproval()
        orderStatusHistoryRepository.save(
            OrderStatusHistory.record(
                order = order,
                fromStatus = from,
                toStatus = OrderStatus.CANCELLED,
                triggeredBy = TriggeredBy.SYSTEM,
            ),
        )
    }

    /**
     * 옵션조합별로 합산해 한 번씩만 되돌린다. 차감과 같이 id 오름차순 — 교차 주문 간 데드락 예방.
     *
     * 복원 실패는 환불을 되돌리지 않는다. 돈은 이미 PG에서 나갔고, 여기서 트랜잭션을 깨면
     * 원장 차감과 주문그룹 전이까지 사라져 대사가 "PG는 취소, 우리는 아님"으로 사람을 부른다.
     * 되돌리지 못한 수량은 ERROR로 남겨 재고만 사람이 맞추게 한다.
     */
    private fun restoreStock(claim: Claim): Int {
        val quantities =
            claim.items
                .groupBy { it.orderItem.optionCombinationId }
                .mapValues { (_, items) -> items.sumOf { it.quantity } }
        var skipped = 0
        quantities.toSortedMap().forEach { (optionCombinationId, quantity) ->
            val restored = optionStockUseCase.restoreStock(RestoreOptionStockCommand(optionCombinationId, quantity))
            if (restored.status == RestoreOptionStockStatus.RESTORED) return@forEach
            skipped++
            log.error(
                "환불 집행 중 재고 복원 실패 — 환불은 유지하고 재고만 수동 보정 필요. claimPublicId={}, optionCombinationId={}, quantity={}, 사유={}, 현재재고={}",
                claim.publicId,
                optionCombinationId,
                quantity,
                restored.status,
                restored.currentStock,
            )
        }
        return skipped
    }

    /** 멱등키는 클레임 단위다 — 그룹 키를 쓰면 같은 그룹의 두 번째 부분취소가 조용히 건너뛰어진다. */
    private fun releasePoint(
        claim: Claim,
        group: OrderGroup,
        amount: Long,
    ): Long {
        if (amount <= 0L) return 0L
        pointUseCase.releasePoint(
            ReleasePointCommand(
                userId = group.buyerId,
                amount = amount,
                idempotencyKey = OrderPointLedger.claimReleaseKey(claim.publicId),
                sourceType = OrderPointLedger.CLAIM_SOURCE_TYPE,
                sourceId = claim.id,
            ),
        )
        return amount
    }

    /**
     * 품목 환불액은 요청 시점에 claim_items에 확정 저장된 값을 그대로 쓴다.
     * 배송비 환불액과 적립금 복원액은 저장 컬럼이 없어 다시 계산하는데, 입력(단가·수량·할인·배송비·사용 적립금)이
     * 전부 불변이라 요청 시점과 같은 값이 나온다.
     */
    private fun refundAmounts(
        claim: Claim,
        order: Order,
    ): RefundAmounts {
        val targets = claim.claimedQuantityByOrderItemId()
        val usedPoint = order.orderGroup.usedPoint
        val groupOrders = if (usedPoint > 0L) orderRepository.findAllByOrderGroupId(order.orderGroup.id) else listOf(order)
        val shippingFeeRefund = claimAmountCalculator.shippingFeeRefund(order, groupOrders, usedPoint, targets)
        return RefundAmounts(
            refundAmount = Math.addExact(claim.itemRefundTotal(), shippingFeeRefund),
            pointRefund = claimAmountCalculator.pointRefund(order, groupOrders, usedPoint, targets),
        )
    }

    private fun loadClaim(claimPublicId: String): Claim =
        claimRepository.findByPublicId(claimPublicId)
            ?: throw BusinessException(OrderErrorCode.CLAIM_NOT_FOUND)

    private fun loadOrder(claim: Claim): Order =
        orderRepository.findById(claim.orderId)
            ?: throw BusinessException(OrderErrorCode.ORDER_NOT_FOUND)

    /** 선점에 성공한 집행이 PG에 보낼 것들. */
    data class RefundReservation(
        val claimPublicId: String,
        val orderGroupId: Long,
        val refundAmount: Long,
    )

    private data class RefundAmounts(
        val refundAmount: Long,
        val pointRefund: Long,
    )
}
