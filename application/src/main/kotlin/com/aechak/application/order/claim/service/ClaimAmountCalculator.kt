package com.aechak.application.order.claim.service

import com.aechak.domain.order.order.Order
import com.aechak.domain.order.order.OrderItem
import org.springframework.stereotype.Service

/**
 * 환불액 계산. 품목 환불액은 claim_items에 확정 저장되고, 이후 승인·집행은 저장된 값을 그대로 쓴다.
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
        val allocation = allocatePoint(groupOrders, usedPoint)
        return ClaimAmounts(
            itemRefunds = itemRefunds(order, targets, allocation),
            shippingFeeRefund = shippingRefund(order, targets, allocation),
        )
    }

    /** 이미 저장된 클레임의 배송비 환불액 — 품목 환불액은 claim_items에서 읽는다. */
    fun shippingFeeRefund(
        order: Order,
        groupOrders: List<Order>,
        usedPoint: Long,
        targets: Map<Long, Int>,
    ): Long = shippingRefund(order, targets, allocatePoint(groupOrders, usedPoint))

    private fun itemRefunds(
        order: Order,
        targets: Map<Long, Int>,
        allocation: PointAllocation,
    ): Map<Long, Long> {
        val orderItems = order.items.associateBy { it.id }
        return targets.mapValues { (orderItemId, quantity) ->
            val item = orderItems.getValue(orderItemId)
            val gross = item.unitPriceSnapshot * quantity - prorate(item.discountAllocatedAmount, quantity, item.quantity)
            val point = prorate(allocation.ofItem(orderItemId), quantity, item.quantity)
            // 안분액은 품목 금액을 넘지 않게 배분된다 — 음수가 나오면 배분이 깨진 것이라 드러낸다
            require(gross >= point) { "품목 환불액이 음수입니다 (orderItemId=$orderItemId, gross=$gross, point=$point)" }
            gross - point
        }
    }

    /**
     * 주문 전체를 취소하면 배송비를 돌려주고, 일부만 취소하면 돌려주지 않는다.
     * 남은 금액이 무료배송 임계 아래로 떨어지는 경우의 재부과는 하지 않는다.
     * 배송비에 안분된 적립금은 현금으로 나가지 않았으므로 환불액에서 뺀다.
     */
    private fun shippingRefund(
        order: Order,
        targets: Map<Long, Int>,
        allocation: PointAllocation,
    ): Long {
        if (!coversWholeOrder(order, targets)) return 0L
        return order.sellerShippingFee - allocation.ofShipping(order.id)
    }

    private fun coversWholeOrder(
        order: Order,
        targets: Map<Long, Int>,
    ): Boolean = order.items.all { (targets[it.id] ?: 0) >= it.quantity }

    /**
     * 적립금은 그룹 단일 값이고 주문·주문품목에 안분 컬럼이 없다 — 결제 대상 줄(품목 금액과 배송비)에
     * 비례해 나눈다. 상품금액만 분모로 잡으면 적립금이 상품금액을 넘는 주문에서 환불액이 결제액을 넘는다.
     */
    private fun allocatePoint(
        groupOrders: List<Order>,
        usedPoint: Long,
    ): PointAllocation {
        if (usedPoint <= 0L) return PointAllocation.NONE
        val lines = payableLines(groupOrders)
        val base = lines.sumOf { it.amount }
        if (base <= 0L) return PointAllocation.NONE

        val allocatable = minOf(usedPoint, base)
        val shares = lines.associate { it.key to allocatable * it.amount / base }.toMutableMap()
        distributeResidual(lines, shares, allocatable - shares.values.sum())
        return PointAllocation(
            itemShares = shares.filterKeys { it.orderItemId != null }.mapKeys { it.key.orderItemId!! },
            shippingShares = shares.filterKeys { it.orderItemId == null }.mapKeys { it.key.orderId },
        )
    }

    /** 나누어떨어지지 않은 잔차를 큰 금액 줄부터 1원씩 싣는다. 합이 사용액과 어긋나면 환불 총액이 결제액과 갈린다. */
    private fun distributeResidual(
        lines: List<PayableLine>,
        shares: MutableMap<LineKey, Long>,
        residualAmount: Long,
    ) {
        var residual = residualAmount
        if (residual <= 0L) return
        lines
            .sortedWith(
                compareByDescending<PayableLine> { it.amount }
                    .thenBy { it.key.orderId }
                    .thenBy { it.key.orderItemId ?: Long.MAX_VALUE },
            ).forEach { line ->
                if (residual <= 0L) return
                // 한 줄에 몰아 싣으면 금액이 작은 줄의 환불액이 음수가 된다 — 남은 여유만큼만 싣는다
                val added = minOf(residual, line.amount - shares.getValue(line.key))
                shares[line.key] = shares.getValue(line.key) + added
                residual -= added
            }
    }

    private fun payableLines(groupOrders: List<Order>): List<PayableLine> =
        groupOrders
            .sortedBy { it.id }
            .flatMap { order ->
                order.items.sortedBy { it.id }.map { PayableLine(LineKey(order.id, it.id), netAmount(it)) } +
                    PayableLine(LineKey(order.id, null), order.sellerShippingFee)
            }.filter { it.amount > 0L }

    private fun netAmount(item: OrderItem): Long = item.unitPriceSnapshot * item.quantity - item.discountAllocatedAmount

    private fun prorate(
        amount: Long,
        claimedQuantity: Int,
        orderedQuantity: Int,
    ): Long = if (claimedQuantity >= orderedQuantity) amount else amount * claimedQuantity / orderedQuantity

    /** 배송비 줄은 orderItemId가 없다. */
    private data class LineKey(
        val orderId: Long,
        val orderItemId: Long?,
    )

    private data class PayableLine(
        val key: LineKey,
        val amount: Long,
    )

    private class PointAllocation(
        private val itemShares: Map<Long, Long>,
        private val shippingShares: Map<Long, Long>,
    ) {
        fun ofItem(orderItemId: Long): Long = itemShares[orderItemId] ?: 0L

        fun ofShipping(orderId: Long): Long = shippingShares[orderId] ?: 0L

        companion object {
            val NONE = PointAllocation(emptyMap(), emptyMap())
        }
    }
}
