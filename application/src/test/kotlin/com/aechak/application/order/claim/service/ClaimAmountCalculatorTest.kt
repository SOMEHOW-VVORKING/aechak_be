package com.aechak.application.order.claim.service

import com.aechak.domain.order.group.DeliveryAddressSnapshot
import com.aechak.domain.order.group.OrderGroup
import com.aechak.domain.order.order.Order
import com.aechak.domain.order.order.OrderItem
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 환불액 계산 계약. 지켜야 하는 불변식은 하나다 —
 * 그룹의 모든 주문을 전량 취소한 환불액 합이 결제액과 같아야 한다.
 * 깨지면 PG로 내보내는 취소 금액이 실제 결제액과 갈려 대사가 불일치를 낸다.
 */
class ClaimAmountCalculatorTest {
    private val calculator = ClaimAmountCalculator()

    @Test
    fun `적립금을 쓴 단일 주문의 전량 취소 환불액은 결제액과 같다`() {
        val group = group(usedPoint = 10_000L, productAmount = 51_000L, shippingFee = 3_000L)
        val order = order(group, id = 1L, shippingFee = 3_000L, lines = listOf(20_000L to 1, 15_500L to 2))

        val amounts = calculator.calculate(order, listOf(order), group.usedPoint, fullTargets(order))

        assertEquals(group.finalPaymentAmount, amounts.total)
    }

    @Test
    fun `적립금이 상품금액을 넘어도 환불액은 결제액과 같다`() {
        val group = group(usedPoint = 2_000L, productAmount = 1_000L, shippingFee = 3_000L)
        val order = order(group, id = 1L, shippingFee = 3_000L, lines = listOf(1_000L to 1))

        val amounts = calculator.calculate(order, listOf(order), group.usedPoint, fullTargets(order))

        assertEquals(2_000L, group.finalPaymentAmount)
        assertEquals(group.finalPaymentAmount, amounts.total)
        assertTrue(amounts.itemRefunds.values.all { it >= 0L }, "품목 환불액이 음수면 안 된다")
    }

    @Test
    fun `여러 셀러 주문의 전량 취소 환불액 합이 결제액과 같다`() {
        val group = group(usedPoint = 7_777L, productAmount = 61_000L, shippingFee = 5_500L)
        val first = order(group, id = 1L, shippingFee = 3_000L, lines = listOf(20_000L to 1, 15_500L to 2))
        val second = order(group, id = 2L, shippingFee = 2_500L, lines = listOf(10_000L to 1))
        val orders = listOf(first, second)

        val total = orders.sumOf { calculator.calculate(it, orders, group.usedPoint, fullTargets(it)).total }

        assertEquals(group.finalPaymentAmount, total)
    }

    /** 금액 두 개를 Long으로 곱하면 범위를 넘겨 안분액이 음수가 된다. */
    @Test
    fun `금액이 커도 안분액이 음수로 뒤집히지 않는다`() {
        val productAmount = 5_000_000_000L + 4_000_000_000L
        val group = group(usedPoint = 3_000_000_000L, productAmount = productAmount, shippingFee = 3_000L)
        val first = order(group, id = 1L, shippingFee = 3_000L, lines = listOf(100_000_000L to 50))
        val second = order(group, id = 2L, shippingFee = 0L, lines = listOf(100_000_000L to 40))
        val orders = listOf(first, second)

        val secondAmounts = calculator.calculate(second, orders, group.usedPoint, fullTargets(second))

        assertTrue(secondAmounts.itemRefunds.values.all { it >= 0L }, "안분액: ${secondAmounts.itemRefunds}")
        assertTrue(secondAmounts.total <= 4_000_000_000L, "한 주문의 환불액이 그 주문 상품금액을 넘으면 안 된다")
        assertEquals(
            group.finalPaymentAmount,
            orders.sumOf { calculator.calculate(it, orders, group.usedPoint, fullTargets(it)).total },
        )
    }

    @Test
    fun `부분 취소는 배송비를 돌려주지 않는다`() {
        val group = group(usedPoint = 0L, productAmount = 51_000L, shippingFee = 3_000L)
        val order = order(group, id = 1L, shippingFee = 3_000L, lines = listOf(20_000L to 1, 15_500L to 2))
        val secondItemId = order.items[1].id

        val amounts = calculator.calculate(order, listOf(order), 0L, mapOf(secondItemId to 1))

        assertEquals(0L, amounts.shippingFeeRefund)
        assertEquals(15_500L, amounts.total)
    }

    @Test
    fun `적립금을 쓰지 않으면 전량 취소 환불액은 상품금액과 배송비의 합이다`() {
        val group = group(usedPoint = 0L, productAmount = 51_000L, shippingFee = 3_000L)
        val order = order(group, id = 1L, shippingFee = 3_000L, lines = listOf(20_000L to 1, 15_500L to 2))

        val amounts = calculator.calculate(order, listOf(order), 0L, fullTargets(order))

        assertEquals(54_000L, amounts.total)
        assertEquals(3_000L, amounts.shippingFeeRefund)
    }

    private fun fullTargets(order: Order): Map<Long, Int> = order.items.associate { it.id to it.quantity }

    private fun group(
        usedPoint: Long,
        productAmount: Long,
        shippingFee: Long,
    ): OrderGroup =
        OrderGroup.create(
            buyerId = 1L,
            deliveryAddressId = 1L,
            deliveryAddress =
                DeliveryAddressSnapshot(
                    receiverNameEnc = "enc",
                    contactNumberEnc = "enc",
                    zipCode = "12345",
                    baseAddress = "서울시 애착구 멍냥로 1",
                    detailAddress = null,
                    deliveryMemo = null,
                ),
            usedPoint = usedPoint,
            totalProductAmount = productAmount,
            totalShippingFee = shippingFee,
            idempotencyKey = "calc-$usedPoint-$productAmount-$shippingFee",
            expiresAt = LocalDateTime.of(2026, 10, 18, 12, 0, 0),
        )

    /** 식별자는 저장 시점에 DB가 넣는다 — 안분 순서가 식별자에 걸려 있어 여기서 직접 넣는다. */
    private fun order(
        group: OrderGroup,
        id: Long,
        shippingFee: Long,
        lines: List<Pair<Long, Int>>,
    ): Order =
        Order
            .create(
                orderGroup = group,
                sellerId = id,
                sellerNameSnapshot = "멍냥상회$id",
                allocatedCouponDiscount = 0L,
                sellerShippingFee = shippingFee,
                baseShippingFeeSnapshot = shippingFee,
                freeShippingThresholdSnapshot = null,
                items =
                    lines.mapIndexed { index, (unitPrice, quantity) ->
                        OrderItem
                            .of(
                                productId = index + 1L,
                                optionCombinationId = index + 1L,
                                optionNameSnapshot = "기본 / ${index + 1}개",
                                quantity = quantity,
                                unitPriceSnapshot = unitPrice,
                                discountAllocatedAmount = 0L,
                                productVersionId = index + 1L,
                            ).also { setId(it, id * 100 + index) }
                    },
            ).also { setId(it, id) }

    private fun setId(
        target: Any,
        id: Long,
    ) {
        target.javaClass
            .getDeclaredField("id")
            .apply { isAccessible = true }
            .setLong(target, id)
    }
}
