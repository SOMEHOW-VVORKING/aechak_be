package com.aechak.domain.order.order

import com.aechak.common.error.BusinessException
import com.aechak.domain.order.error.OrderErrorCode
import com.aechak.domain.order.group.DeliveryAddressSnapshot
import com.aechak.domain.order.group.OrderGroup
import com.aechak.domain.order.order.enums.ConfirmType
import com.aechak.domain.order.order.enums.OrderStatus
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * 결제 이후 주문 상태머신 계약. 깨지면 발주 확인·발송·배송완료를 건너뛰는 전이가 열린다 —
 * 그 상태가 되면 발주 확인 없는 발송이나 송장 없는 배송완료가 성립한다.
 */
class OrderTransitionTest {
    @Test
    fun `결제완료 주문은 발주 확인으로 상품 준비중이 된다`() {
        val order = paidOrder()

        order.accept()

        assertEquals(OrderStatus.PREPARING, order.status)
    }

    @Test
    fun `이미 발주 확인된 주문의 재확인은 거절된다`() {
        val order = paidOrder().apply { accept() }

        val exception = assertFailsWith<BusinessException> { order.accept() }

        assertEquals(OrderErrorCode.ORDER_NOT_ACCEPTABLE, exception.errorCode)
        assertEquals(OrderStatus.PREPARING, order.status, "거절된 전이가 상태를 바꾸면 안 된다")
    }

    @Test
    fun `결제대기 주문은 발주 확인할 수 없다`() {
        val exception = assertFailsWith<BusinessException> { order().accept() }

        assertEquals(OrderErrorCode.ORDER_NOT_ACCEPTABLE, exception.errorCode)
    }

    @Test
    fun `상품 준비중은 발송 대기로 간다`() {
        val order = paidOrder().apply { accept() }

        order.markDispatchPending()

        assertEquals(OrderStatus.DISPATCH_PENDING, order.status)
    }

    @Test
    fun `발송 대기를 건너뛴 발송 확정은 거절된다`() {
        val order = paidOrder().apply { accept() }

        val exception = assertFailsWith<BusinessException> { order.markShipping() }

        assertEquals(OrderErrorCode.ORDER_NOT_DISPATCHABLE, exception.errorCode)
    }

    @Test
    fun `발송 대기는 배송중으로 간다`() {
        val order = dispatchPendingOrder()

        order.markShipping()

        assertEquals(OrderStatus.SHIPPING, order.status)
    }

    @Test
    fun `배송완료는 구매확정 기한을 함께 받는다`() {
        val order = dispatchPendingOrder().apply { markShipping() }
        val dueAt = LocalDateTime.of(2026, 10, 23, 0, 0)

        order.markDelivered(dueAt)

        assertEquals(OrderStatus.DELIVERED, order.status)
        assertEquals(dueAt, order.autoConfirmDueAt)
    }

    @Test
    fun `배송중이 아닌 주문은 배송완료로 갈 수 없다`() {
        val order = dispatchPendingOrder()

        val exception = assertFailsWith<BusinessException> { order.markDelivered(LocalDateTime.now()) }

        assertEquals(OrderErrorCode.INVALID_ORDER_STATUS_TRANSITION, exception.errorCode)
        assertNull(order.autoConfirmDueAt, "거절된 전이가 기한을 남기면 안 된다")
    }

    @Test
    fun `배송완료 주문은 구매확정된다`() {
        val order = deliveredOrder()

        order.confirmPurchase(ConfirmType.MANUAL)

        assertEquals(OrderStatus.PURCHASE_CONFIRMED, order.status)
        assertEquals(ConfirmType.MANUAL, order.confirmType)
    }

    @Test
    fun `배송완료 주문은 사전 안내 시각을 받고 재안내는 시각을 덮지 않는다`() {
        val order = deliveredOrder()
        val first = LocalDateTime.of(2026, 10, 22, 2, 0)

        order.markConfirmNotified(first)
        order.markConfirmNotified(first.plusDays(1))

        assertEquals(first, order.purchaseConfirmNotifiedAt, "재안내가 시각을 덮으면 안내 이력의 기준이 흔들린다")
    }

    @Test
    fun `배송완료가 아닌 주문에는 사전 안내 시각을 남기지 않는다`() {
        val order = dispatchPendingOrder()

        val exception = assertFailsWith<BusinessException> { order.markConfirmNotified(LocalDateTime.now()) }

        assertEquals(OrderErrorCode.ORDER_NOT_CONFIRMABLE, exception.errorCode)
        assertNull(order.purchaseConfirmNotifiedAt)
    }

    @Test
    fun `발송 대기 주문은 즉시취소가 막히지만 클레임 승인 경로로는 취소된다`() {
        val immediate = dispatchPendingOrder()
        val byClaim = dispatchPendingOrder()

        val exception = assertFailsWith<BusinessException> { immediate.cancel() }
        byClaim.cancelByClaimApproval()

        assertEquals(OrderErrorCode.CANNOT_CANCEL_SHIPPED, exception.errorCode)
        assertEquals(OrderStatus.CANCELLED, byClaim.status)
    }

    @Test
    fun `결제 전 주문은 클레임 승인 경로로 취소되지 않는다`() {
        val exception = assertFailsWith<BusinessException> { order().cancelByClaimApproval() }

        assertEquals(OrderErrorCode.INVALID_ORDER_STATUS_TRANSITION, exception.errorCode)
    }

    @Test
    fun `배송이 시작된 주문은 클레임 승인으로도 취소되지 않는다`() {
        val order = dispatchPendingOrder().apply { markShipping() }

        val exception = assertFailsWith<BusinessException> { order.cancelByClaimApproval() }

        assertEquals(OrderErrorCode.INVALID_ORDER_STATUS_TRANSITION, exception.errorCode)
    }

    private fun deliveredOrder(): Order =
        dispatchPendingOrder().apply {
            markShipping()
            markDelivered(LocalDateTime.of(2026, 10, 23, 0, 0))
        }

    private fun dispatchPendingOrder(): Order =
        paidOrder().apply {
            accept()
            markDispatchPending()
        }

    private fun paidOrder(): Order = order().apply { markPaid() }

    private fun order(): Order =
        Order.create(
            orderGroup = orderGroup(),
            sellerId = 77L,
            sellerNameSnapshot = "멍멍상회",
            allocatedCouponDiscount = 0L,
            sellerShippingFee = 3_000L,
            baseShippingFeeSnapshot = 3_000L,
            freeShippingThresholdSnapshot = null,
            items =
                listOf(
                    OrderItem.of(
                        productId = 1L,
                        optionCombinationId = 2L,
                        optionNameSnapshot = "기본 / 1개",
                        quantity = 1,
                        unitPriceSnapshot = 10_000L,
                        discountAllocatedAmount = 0L,
                        productVersionId = 3L,
                    ),
                ),
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
            idempotencyKey = "order-transition-test",
            expiresAt = LocalDateTime.of(2026, 10, 18, 12, 0, 0),
        )
}
