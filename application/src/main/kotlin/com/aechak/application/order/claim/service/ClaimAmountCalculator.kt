package com.aechak.application.order.claim.service

import com.aechak.domain.order.order.Order
import com.aechak.domain.order.order.OrderItem
import org.springframework.stereotype.Service

/**
 * 환불액 계산. 결과는 claim_items에 확정 저장되고, 이후 승인·집행은 저장된 값을 그대로 쓴다.
 */
@Service
class ClaimAmountCalculator {
    /**
     * @param groupOrders 적립금 안분의 분모가 되는 같은 주문그룹의 주문 전체
     * @param targets 주문품목 식별자 → 취소 수량
     */
    fun calculate(
        order: Order,
        groupOrders: List<Order>,
        usedPoint: Long,
        targets: Map<Long, Int>,
    ): ClaimAmounts {
        val pointShares = pointSharesByOrderItemId(groupOrders, usedPoint)
        val orderItems = order.items.associateBy { it.id }
        val itemRefunds =
            targets.mapValues { (orderItemId, quantity) ->
                val item = orderItems.getValue(orderItemId)
                val gross = item.unitPriceSnapshot * quantity - prorate(item.discountAllocatedAmount, quantity, item.quantity)
                val point = prorate(pointShares[orderItemId] ?: 0L, quantity, item.quantity)
                (gross - point).coerceAtLeast(0L)
            }
        return ClaimAmounts(itemRefunds = itemRefunds, shippingFeeRefund = shippingFeeRefund(order, targets))
    }

    /**
     * 주문 전체를 취소하면 배송비를 전액 돌려주고, 일부만 취소하면 배송비는 돌려주지 않는다.
     * 남은 금액이 무료배송 임계 아래로 떨어지는 경우의 재부과는 하지 않는다.
     */
    fun shippingFeeRefund(
        order: Order,
        targets: Map<Long, Int>,
    ): Long = if (coversWholeOrder(order, targets)) order.sellerShippingFee else 0L

    private fun coversWholeOrder(
        order: Order,
        targets: Map<Long, Int>,
    ): Boolean = order.items.all { (targets[it.id] ?: 0) >= it.quantity }

    /**
     * 적립금은 그룹 단일 값이고 주문·주문품목에 안분 컬럼이 없다 — 품목 금액 비례로 나누고
     * 나누어떨어지지 않는 잔차는 마지막 품목에 싣는다. 합이 사용액과 어긋나면 환불 총액이 결제액과 갈린다.
     */
    private fun pointSharesByOrderItemId(
        groupOrders: List<Order>,
        usedPoint: Long,
    ): Map<Long, Long> {
        if (usedPoint <= 0L) return emptyMap()
        val lines = groupOrders.sortedBy { it.id }.flatMap { order -> order.items.sortedBy { it.id } }
        val netTotal = lines.sumOf { netAmount(it) }
        if (lines.isEmpty() || netTotal <= 0L) return emptyMap()

        val shares = lines.associate { it.id to usedPoint * netAmount(it) / netTotal }.toMutableMap()
        val lastId = lines.last().id
        shares[lastId] = shares.getValue(lastId) + (usedPoint - shares.values.sum())
        return shares
    }

    private fun netAmount(item: OrderItem): Long = item.unitPriceSnapshot * item.quantity - item.discountAllocatedAmount

    private fun prorate(
        amount: Long,
        claimedQuantity: Int,
        orderedQuantity: Int,
    ): Long = if (claimedQuantity >= orderedQuantity) amount else amount * claimedQuantity / orderedQuantity
}
