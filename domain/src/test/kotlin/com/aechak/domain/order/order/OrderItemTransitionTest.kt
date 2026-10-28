package com.aechak.domain.order.order

import com.aechak.common.error.BusinessException
import com.aechak.domain.order.error.OrderErrorCode
import com.aechak.domain.order.order.enums.OrderItemStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * 주문품목 상태머신 계약. 깨지면 이미 취소된 품목이 다시 취소돼 환불·재고 복원이 두 번 일어나거나,
 * 회수하지 않은 반품이 회수 완료로 넘어간다.
 */
class OrderItemTransitionTest {
    @Test
    fun `주문된 품목은 취소로 간다`() {
        val item = orderItem()

        item.markCancelled()

        assertEquals(OrderItemStatus.CANCELLED, item.itemStatus)
    }

    @Test
    fun `취소된 품목은 다시 취소할 수 없다`() {
        val item = orderItem().apply { markCancelled() }

        val exception = assertFailsWith<BusinessException> { item.markCancelled() }

        assertEquals(OrderErrorCode.INVALID_ORDER_ITEM_STATUS_TRANSITION, exception.errorCode)
        assertEquals(OrderItemStatus.CANCELLED, item.itemStatus)
    }

    @Test
    fun `반품 요청은 회수 완료로 간다`() {
        val item = orderItem().apply { markReturnRequested() }

        item.markReturned()

        assertEquals(OrderItemStatus.RETURNED, item.itemStatus)
    }

    @Test
    fun `회수하지 않은 품목은 회수 완료로 갈 수 없다`() {
        val exception = assertFailsWith<BusinessException> { orderItem().markReturned() }

        assertEquals(OrderErrorCode.INVALID_ORDER_ITEM_STATUS_TRANSITION, exception.errorCode)
    }

    @Test
    fun `취소된 품목은 반품 교환 요청 대상이 아니다`() {
        val item = orderItem().apply { markCancelled() }

        assertFailsWith<BusinessException> { item.markReturnRequested() }
        assertFailsWith<BusinessException> { item.markExchangeRequested() }
    }

    @Test
    fun `주문된 품목은 교환 요청으로 간다`() {
        val item = orderItem()

        item.markExchangeRequested()

        assertEquals(OrderItemStatus.EXCHANGE_REQUESTED, item.itemStatus)
    }

    @Test
    fun `거부된 반품 교환 요청은 주문 상태로 되돌아간다`() {
        val returnRequested = orderItem().apply { markReturnRequested() }
        val exchangeRequested = orderItem().apply { markExchangeRequested() }

        returnRequested.revertClaimRequest()
        exchangeRequested.revertClaimRequest()

        // 되돌리지 않으면 거부된 품목은 다시 반품·교환을 요청할 수 없다
        assertEquals(OrderItemStatus.ORDERED, returnRequested.itemStatus)
        assertEquals(OrderItemStatus.ORDERED, exchangeRequested.itemStatus)
        returnRequested.markReturnRequested()
        assertEquals(OrderItemStatus.RETURN_REQUESTED, returnRequested.itemStatus)
    }

    @Test
    fun `회수 완료된 품목은 되돌릴 수 없다`() {
        val item = orderItem().apply { markReturnRequested() }
        item.markReturned()

        val exception = assertFailsWith<BusinessException> { item.revertClaimRequest() }

        assertEquals(OrderErrorCode.INVALID_ORDER_ITEM_STATUS_TRANSITION, exception.errorCode)
        assertEquals(OrderItemStatus.RETURNED, item.itemStatus)
    }

    @Test
    fun `이행 대상이 아닌 상태는 취소와 회수 완료뿐이다`() {
        assertEquals(setOf(OrderItemStatus.CANCELLED, OrderItemStatus.RETURNED), OrderItemStatus.RESOLVED)
    }

    private fun orderItem() =
        OrderItem.of(
            productId = 1L,
            optionCombinationId = 10L,
            optionNameSnapshot = "기본 / 1개",
            quantity = 2,
            unitPriceSnapshot = 10_000L,
            discountAllocatedAmount = 0L,
            productVersionId = 1L,
        )
}
