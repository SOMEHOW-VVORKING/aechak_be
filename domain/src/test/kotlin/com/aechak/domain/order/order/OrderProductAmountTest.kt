package com.aechak.domain.order.order

import com.aechak.domain.order.group.DeliveryAddressSnapshot
import com.aechak.domain.order.group.OrderGroup
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 구매확정 적립의 기준 금액 계약. 깨지면 배송비나 할인에 적립금이 붙는다 —
 * 배송비까지 적립되면 주문마다 셀러 배송 정책에 따라 지급액이 달라진다.
 */
class OrderProductAmountTest {
    @Test
    fun `상품금액은 단가와 수량의 곱을 더한 값이다`() {
        val order = order(items = listOf(line(unitPrice = 10_000L, quantity = 2), line(unitPrice = 3_500L, quantity = 1)))

        assertEquals(23_500L, order.productAmountAfterDiscount())
    }

    @Test
    fun `안분된 할인액을 뺀다`() {
        val order = order(items = listOf(line(unitPrice = 10_000L, quantity = 2, discount = 500L)))

        assertEquals(19_500L, order.productAmountAfterDiscount())
    }

    @Test
    fun `배송비는 상품금액에 들어가지 않는다`() {
        val order = order(items = listOf(line(unitPrice = 10_000L, quantity = 1)), shippingFee = 3_000L)

        assertEquals(10_000L, order.productAmountAfterDiscount())
    }

    @Test
    fun `품목별 상품금액의 합이 주문 상품금액이다`() {
        // 적립 기준(주문 합계)과 정산 원장의 품목별 기준이 갈리면 같은 주문에 두 가지 상품금액이 생긴다
        val items = listOf(line(unitPrice = 10_000L, quantity = 2, discount = 500L), line(unitPrice = 3_500L, quantity = 3))
        val order = order(items = items, shippingFee = 3_000L)

        assertEquals(order.productAmountAfterDiscount(), order.items.sumOf { it.productAmountAfterDiscount() })
        assertEquals(listOf(19_500L, 10_500L), order.items.map { it.productAmountAfterDiscount() })
    }

    private fun line(
        unitPrice: Long,
        quantity: Int,
        discount: Long = 0L,
    ): OrderItem =
        OrderItem.of(
            productId = 1L,
            optionCombinationId = 2L,
            optionNameSnapshot = "기본 / 1개",
            quantity = quantity,
            unitPriceSnapshot = unitPrice,
            discountAllocatedAmount = discount,
            productVersionId = 3L,
        )

    private fun order(
        items: List<OrderItem>,
        shippingFee: Long = 0L,
    ): Order =
        Order.create(
            orderGroup = orderGroup(),
            sellerId = 77L,
            sellerNameSnapshot = "멍멍상회",
            allocatedCouponDiscount = 0L,
            sellerShippingFee = shippingFee,
            baseShippingFeeSnapshot = shippingFee,
            freeShippingThresholdSnapshot = null,
            items = items,
        )

    private fun orderGroup(): OrderGroup =
        OrderGroup.create(
            buyerId = 1L,
            deliveryAddressId = 100L,
            deliveryAddress =
                DeliveryAddressSnapshot(
                    receiverNameEnc = "enc-receiver",
                    contactNumberEnc = "enc-contact",
                    zipCode = "06236",
                    baseAddress = "서울특별시 강남구 테헤란로 1",
                    detailAddress = null,
                    deliveryMemo = null,
                ),
            usedPoint = 0L,
            totalProductAmount = 10_000L,
            totalShippingFee = 3_000L,
            idempotencyKey = "order-product-amount-test",
            expiresAt = LocalDateTime.of(2026, 10, 18, 12, 0, 0),
        )
}
