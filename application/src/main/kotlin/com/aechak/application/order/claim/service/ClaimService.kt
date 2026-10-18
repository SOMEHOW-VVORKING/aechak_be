package com.aechak.application.order.claim.service

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
import com.aechak.domain.order.claim.repository.ClaimRepository
import com.aechak.domain.order.error.OrderErrorCode
import com.aechak.domain.order.order.Order
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
    @param:Value("\${order.claim.auto-approve-days:2}") private val autoApproveDays: Long,
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
        claimRepository.save(claim)
    }

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
