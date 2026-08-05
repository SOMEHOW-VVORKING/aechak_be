package com.aechak.domain.order.cart

import com.aechak.common.error.BusinessException
import com.aechak.common.error.CommonErrorCode
import com.aechak.domain.order.error.OrderErrorCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** 계약 테스트. 깨지면 재담기가 새 행을 만들거나 품목 종류 상한이 뚫린 것임. */
class CartTest {
    private fun cart() = Cart.create(buyerId = 1L)

    @Test
    fun `새 옵션 조합을 담으면 라인이 추가된다`() {
        val cart = cart()

        val item = cart.addItem(optionCombinationId = 10L, quantity = 2)

        assertEquals(1, cart.items.size, "빈 장바구니에 담으면 라인이 1개여야 한다")
        assertEquals(10L, item.optionCombinationId, "담은 옵션 조합 id가 라인에 실려야 한다")
        assertEquals(2, item.quantity, "담은 수량이 라인에 실려야 한다")
    }

    @Test
    fun `동일 옵션 조합 재담기는 새 행이 아니라 기존 수량에 누적된다`() {
        val cart = cart()
        cart.addItem(10L, 2)

        val merged = cart.addItem(10L, 3)

        assertEquals(1, cart.items.size, "재담기는 라인을 늘리지 않아야 한다(INV-01)")
        assertEquals(5, merged.quantity, "재담기는 기존 수량 2에 3을 누적해 5여야 한다")
    }

    @Test
    fun `다른 옵션 조합은 새 라인으로 담긴다`() {
        val cart = cart()
        cart.addItem(10L, 2)

        cart.addItem(11L, 3)

        assertEquals(2, cart.items.size, "다른 옵션 조합은 각자 라인이어야 한다")
    }

    @Test
    fun `품목 종류가 100개면 새 조합 담기는 50203으로 차단된다`() {
        val cart = cart()
        repeat(Cart.MAX_ITEM_KINDS) { cart.addItem(it + 1L, 1) }

        val e = assertFailsWith<BusinessException> { cart.addItem(999L, 1) }

        assertEquals(OrderErrorCode.CART_ITEM_LIMIT_EXCEEDED, e.errorCode, "품목 종류 상한 초과는 50203이어야 한다")
    }

    @Test
    fun `품목 종류가 100개여도 기존 라인 누적은 허용된다 - 두 상한은 단위가 다르다`() {
        val cart = cart()
        repeat(Cart.MAX_ITEM_KINDS) { cart.addItem(it + 1L, 1) }

        val merged = cart.addItem(1L, 1)

        assertEquals(2, merged.quantity, "종류 상한은 누적을 막지 않아야 한다")
        assertEquals(Cart.MAX_ITEM_KINDS, cart.items.size, "누적은 라인 수를 바꾸지 않아야 한다")
    }

    @Test
    fun `누적 결과가 라인당 상한 99를 넘으면 90001 검증 실패다`() {
        val cart = cart()
        cart.addItem(10L, 98)

        val e = assertFailsWith<BusinessException> { cart.addItem(10L, 2) }

        assertEquals(CommonErrorCode.INVALID_REQUEST, e.errorCode, "누적 상한 초과는 90001이어야 한다")
    }

    @Test
    fun `수량 1 미만 담기는 50200이다`() {
        val e = assertFailsWith<BusinessException> { cart().addItem(10L, 0) }

        assertEquals(OrderErrorCode.INVALID_CART_ITEM_QUANTITY, e.errorCode, "신규 라인 경로의 수량 0은 50200이어야 한다")
    }

    @Test
    fun `동일 옵션 조합이 담긴 상태의 수량 1 미만 재담기도 50200이다`() {
        val cart = cart()
        cart.addItem(10L, 2)

        val e = assertFailsWith<BusinessException> { cart.addItem(10L, 0) }

        assertEquals(OrderErrorCode.INVALID_CART_ITEM_QUANTITY, e.errorCode, "누적 경로의 수량 0도 50200이어야 한다")
    }
}
