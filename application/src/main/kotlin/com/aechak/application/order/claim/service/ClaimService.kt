package com.aechak.application.order.claim.service

import com.aechak.application.order.claim.port.ClaimDeliveryQueryPort
import com.aechak.application.order.claim.port.ClaimItemProductQueryPort
import com.aechak.application.order.claim.usecase.command.ApproveClaimCommand
import com.aechak.application.order.claim.usecase.command.RejectClaimCommand
import com.aechak.application.order.claim.usecase.command.RequestClaimCommand
import com.aechak.application.order.claim.usecase.command.WithdrawClaimCommand
import com.aechak.application.order.claim.usecase.result.ClaimListResult
import com.aechak.application.order.claim.usecase.result.RequestClaimResult
import com.aechak.common.error.BusinessException
import com.aechak.common.error.CommonErrorCode
import com.aechak.domain.order.claim.Claim
import com.aechak.domain.order.claim.ClaimItem
import com.aechak.domain.order.claim.enums.ClaimType
import com.aechak.domain.order.claim.repository.ClaimRepository
import com.aechak.domain.order.error.OrderErrorCode
import com.aechak.domain.order.order.Order
import com.aechak.domain.order.order.OrderItem
import com.aechak.domain.order.order.repository.OrderRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class ClaimService(
    private val claimRepository: ClaimRepository,
    private val orderRepository: OrderRepository,
    private val claimAmountCalculator: ClaimAmountCalculator,
    private val claimItemProductQueryPort: ClaimItemProductQueryPort,
    private val claimDeliveryQueryPort: ClaimDeliveryQueryPort,
    @param:Value("\${order.claim.auto-approve-days:2}") private val autoApproveDays: Long,
    @param:Value("\${order.claim.return-window-days:7}") private val returnWindowDays: Long,
) {
    fun requestClaim(command: RequestClaimCommand): RequestClaimResult {
        // 주문 행을 먼저 선점한다 — 겹친 요청이 중복 검사를 둘 다 통과하면 같은 품목에 클레임이 두 건 걸린다.
        // 소유권 확인보다 선점이 앞서지만 공개 식별자는 추측 불가이고 선점은 이 트랜잭션 안에서 끝난다
        val order =
            orderRepository
                .findByPublicIdForUpdate(command.orderPublicId)
                ?.takeIf { it.orderGroup.buyerId == command.buyerId }
                ?: throw BusinessException(OrderErrorCode.ORDER_NOT_FOUND)
        val targets = resolveTargets(order, command.targets)
        rejectIfAlreadyClaimed(targets.keys)
        rejectIfReturnWindowClosed(order, command.claimType)

        val amounts =
            claimAmountCalculator.calculate(
                order = order,
                groupOrders = groupOrdersOf(order),
                usedPoint = order.orderGroup.usedPoint,
                targets = targets,
            )
        val claim =
            Claim.request(
                order = order,
                claimType = command.claimType,
                reasonCode = command.reasonCode,
                reasonDetail = command.reasonDetail,
                items =
                    order.items
                        .filter { it.id in targets }
                        .map { ClaimItem.of(it, targets.getValue(it.id), amounts.itemRefunds.getValue(it.id)) },
                autoApproveDueAt = LocalDateTime.now().plusDays(autoApproveDays),
            )
        markRequestedItems(order, command.claimType, targets.keys)
        return RequestClaimResult.of(claimRepository.save(claim), amounts.total)
    }

    fun getClaims(
        orderPublicId: String,
        buyerId: Long,
    ): ClaimListResult {
        val order = ownedOrder(orderPublicId, buyerId)
        val claims = claimRepository.findAllByOrderId(order.id)
        val orderItemIds = claims.flatMap { claim -> claim.items.map { it.orderItem.id } }.distinct()
        val productNames =
            claimItemProductQueryPort
                .findProductNames(orderItemIds)
                .associate { it.orderItemId to it.productName }
        val groupOrders = groupOrdersOf(order)
        val refundAmounts =
            claims.associate { claim ->
                claim.publicId to
                    Math.addExact(
                        claim.itemRefundTotal(),
                        claimAmountCalculator.shippingFeeRefund(
                            order = order,
                            groupOrders = groupOrders,
                            usedPoint = order.orderGroup.usedPoint,
                            targets = claim.claimedQuantityByOrderItemId(),
                        ),
                    )
            }
        return ClaimListResult.of(claims, refundAmounts, productNames)
    }

    fun withdrawClaim(command: WithdrawClaimCommand) {
        val order = ownedOrder(command.orderPublicId, command.buyerId)
        val claim =
            claimRepository
                .findByPublicId(command.claimPublicId)
                ?.takeIf { it.orderId == order.id }
                ?: throw BusinessException(OrderErrorCode.CLAIM_NOT_FOUND)
        claim.withdrawByBuyer()
        revertRequestedItems(claim, order)
        claimRepository.save(claim)
    }

    fun approveClaim(command: ApproveClaimCommand) {
        val claim = sellerClaim(command.claimPublicId, command.sellerId)
        claim.approveBySeller(command.returnCostBearer)
        claimRepository.save(claim)
    }

    fun rejectClaim(command: RejectClaimCommand) {
        val claim = sellerClaim(command.claimPublicId, command.sellerId)
        claim.rejectBySeller(command.rejectionReason)
        // 취소 클레임은 요청 시점에 품목을 건드리지 않았으므로 주문을 읽을 이유도 없다
        if (claim.claimType.requiresCollection()) {
            revertRequestedItems(claim, claimedOrder(claim))
        }
        claimRepository.save(claim)
    }

    /**
     * 반품 기한은 배송완료 시각 기준이다. 전이 이력이 없으면 기산점을 모르는 것이라 요청을 받지 않는다 —
     * 기한 없이 열어 두면 몇 달 전 배송완료 주문까지 반품이 들어온다.
     */
    private fun rejectIfReturnWindowClosed(
        order: Order,
        claimType: ClaimType,
    ) {
        if (!claimType.requiresCollection()) return
        val deliveredAt =
            claimDeliveryQueryPort.findDeliveredAt(order.id)
                ?: throw BusinessException(OrderErrorCode.CLAIM_NOT_REQUESTABLE)
        if (LocalDateTime.now() > deliveredAt.plusDays(returnWindowDays)) {
            throw BusinessException(OrderErrorCode.CLAIM_NOT_REQUESTABLE)
        }
    }

    /** 반품·교환은 요청 시점부터 품목이 잡힌다 — 취소는 환불 집행 때 한 번에 바뀐다. */
    private fun markRequestedItems(
        order: Order,
        claimType: ClaimType,
        orderItemIds: Set<Long>,
    ) {
        when (claimType) {
            ClaimType.CANCEL -> return
            ClaimType.RETURN -> targetItems(order, orderItemIds).forEach { it.markReturnRequested() }
            ClaimType.EXCHANGE -> targetItems(order, orderItemIds).forEach { it.markExchangeRequested() }
        }
    }

    /** 거부·철회된 반품·교환의 품목을 주문 상태로 되돌린다 — 되돌리지 않으면 그 품목은 다시 요청할 수 없다. */
    private fun revertRequestedItems(
        claim: Claim,
        order: Order,
    ) {
        if (!claim.claimType.requiresCollection()) return
        targetItems(order, claim.claimedQuantityByOrderItemId().keys).forEach { it.revertClaimRequest() }
    }

    private fun targetItems(
        order: Order,
        orderItemIds: Set<Long>,
    ): List<OrderItem> = order.items.filter { it.id in orderItemIds }

    private fun claimedOrder(claim: Claim): Order =
        orderRepository.findById(claim.orderId)
            ?: throw BusinessException(OrderErrorCode.ORDER_NOT_FOUND)

    /** 적립금을 쓰지 않은 주문은 안분 분모가 필요 없어 그룹 전체를 읽지 않는다. */
    private fun groupOrdersOf(order: Order): List<Order> =
        if (order.orderGroup.usedPoint > 0L) orderRepository.findAllByOrderGroupId(order.orderGroup.id) else listOf(order)

    /** 대상 품목을 지정하지 않으면 주문 전체를 전량 대상으로 본다. */
    private fun resolveTargets(
        order: Order,
        requested: List<RequestClaimCommand.ClaimTarget>,
    ): Map<Long, Int> {
        if (requested.isEmpty()) return order.items.associate { it.id to it.quantity }
        if (requested.distinctBy { it.orderItemId }.size != requested.size) {
            throw BusinessException(CommonErrorCode.INVALID_REQUEST)
        }
        val orderItems = order.items.associateBy { it.id }
        return requested.associate { target ->
            if (target.quantity <= 0) throw BusinessException(CommonErrorCode.INVALID_REQUEST)
            val item = orderItems[target.orderItemId] ?: throw BusinessException(OrderErrorCode.ORDER_NOT_FOUND)
            if (target.quantity > item.quantity) throw BusinessException(OrderErrorCode.CLAIM_QUANTITY_EXCEEDS_ORDERED)
            // 수량을 쪼갠 요청은 받지 않는다 — order_items에 취소 수량을 담을 자리가 없고 중복 차단도 품목 단위라
            // 남은 수량이 환불도 이행도 후속 클레임도 받지 못한 채 묶인다
            if (target.quantity < item.quantity) throw BusinessException(OrderErrorCode.CLAIM_PARTIAL_QUANTITY_NOT_SUPPORTED)
            target.orderItemId to target.quantity
        }
    }

    private fun rejectIfAlreadyClaimed(orderItemIds: Set<Long>) {
        if (orderItemIds.any { claimRepository.existsActiveByOrderItemId(it) }) {
            throw BusinessException(OrderErrorCode.CLAIM_ALREADY_IN_PROGRESS)
        }
    }

    private fun ownedOrder(
        orderPublicId: String,
        buyerId: Long,
    ): Order =
        orderRepository.findByPublicIdAndBuyerId(orderPublicId, buyerId)
            ?: throw BusinessException(OrderErrorCode.ORDER_NOT_FOUND)

    private fun sellerClaim(
        claimPublicId: String,
        sellerId: Long,
    ): Claim =
        claimRepository.findByPublicIdAndSellerId(claimPublicId, sellerId)
            ?: throw BusinessException(OrderErrorCode.CLAIM_NOT_FOUND)
}
