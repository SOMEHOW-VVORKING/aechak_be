package com.aechak.domain.order.cart

import com.aechak.common.error.BusinessException
import com.aechak.common.error.CommonErrorCode
import com.aechak.domain.order.error.OrderErrorCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** 계약 테스트. 깨지면 라인 수량이 1에서 99 밖으로 나갈 수 있는 것 */
class CartItemTest {
    @Test
    fun `수량을 누적한다`() {
        val item = CartItem.of(optionCombinationId = 10L, quantity = 2)

        item.accumulate(3)

        assertEquals(5, item.quantity, "2에 3을 누적하면 5여야 한다")
    }

    @Test
    fun `누적 결과가 상한과 같으면 허용된다 - 경계 99`() {
        val item = CartItem.of(10L, 98)

        item.accumulate(1)

        assertEquals(CartItem.MAX_QUANTITY, item.quantity, "누적 결과가 상한과 같은 경계값 99는 허용돼야 한다")
    }

    @Test
    fun `누적 결과가 99를 넘으면 90001 검증 실패다`() {
        val item = CartItem.of(10L, 98)

        val e = assertFailsWith<BusinessException> { item.accumulate(2) }

        assertEquals(CommonErrorCode.INVALID_REQUEST, e.errorCode, "상한 초과 누적은 90001이어야 한다")
        assertEquals(98, item.quantity, "실패한 누적은 수량을 바꾸지 않아야 한다")
    }

    @Test
    fun `첫 담기 수량이 상한과 같으면 허용된다 - 경계 99`() {
        assertEquals(CartItem.MAX_QUANTITY, CartItem.of(10L, 99).quantity, "신규 라인의 상한 경계값 99는 허용돼야 한다")
    }

    @Test
    fun `첫 담기 수량이 99를 넘으면 90001 검증 실패다`() {
        val e = assertFailsWith<BusinessException> { CartItem.of(10L, 100) }

        assertEquals(CommonErrorCode.INVALID_REQUEST, e.errorCode, "신규 라인도 상한 99를 넘으면 90001이어야 한다")
    }

    @Test
    fun `수량 1 미만 누적은 50200이다`() {
        val item = CartItem.of(10L, 2)

        val e = assertFailsWith<BusinessException> { item.accumulate(0) }

        assertEquals(OrderErrorCode.INVALID_CART_ITEM_QUANTITY, e.errorCode, "누적 경로도 수량 1 미만은 50200이어야 한다")
    }

    @Test
    fun `음수 누적은 50200이다 - 담기로 수량을 깎을 수 없다`() {
        val item = CartItem.of(10L, 2)

        val e = assertFailsWith<BusinessException> { item.accumulate(-1) }

        assertEquals(OrderErrorCode.INVALID_CART_ITEM_QUANTITY, e.errorCode, "음수 누적도 수량 1 미만이라 50200이어야 한다")
        assertEquals(2, item.quantity, "실패한 누적은 수량을 바꾸지 않아야 한다")
    }

    @Test
    fun `수량 1 미만으로는 만들 수 없다 - 50200`() {
        val e = assertFailsWith<BusinessException> { CartItem.of(10L, 0) }

        assertEquals(OrderErrorCode.INVALID_CART_ITEM_QUANTITY, e.errorCode, "수량 1 미만 생성은 50200이어야 한다")
    }
}
