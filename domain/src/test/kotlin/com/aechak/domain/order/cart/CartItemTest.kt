package com.aechak.domain.order.cart

import com.aechak.common.error.BusinessException
import com.aechak.common.error.CommonErrorCode
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
    fun `첫 담기 수량이 99를 넘으면 90001 검증 실패다`() {
        val e = assertFailsWith<BusinessException> { CartItem.of(10L, 100) }

        assertEquals(CommonErrorCode.INVALID_REQUEST, e.errorCode, "신규 라인도 상한 99를 넘으면 90001이어야 한다")
    }

    @Test
    fun `음수 누적은 90001이다 - 담기로 수량을 깎을 수 없다`() {
        val item = CartItem.of(10L, 2)

        val e = assertFailsWith<BusinessException> { item.accumulate(-1) }

        assertEquals(CommonErrorCode.INVALID_REQUEST, e.errorCode, "음수 누적도 수량 1 미만이라 90001이어야 한다")
        assertEquals(2, item.quantity, "실패한 누적은 수량을 바꾸지 않아야 한다")
    }

    @Test
    fun `수량 1 미만으로는 만들 수 없다 - 90001`() {
        val e = assertFailsWith<BusinessException> { CartItem.of(10L, 0) }

        assertEquals(CommonErrorCode.INVALID_REQUEST, e.errorCode, "수량 1 미만 생성은 90001이어야 한다")
    }

    @Test
    fun `changeQuantity는 누적이 아니라 대입이다`() {
        val item = CartItem.of(10L, 3)

        item.changeQuantity(5)

        assertEquals(5, item.quantity, "3에 5를 더한 8이 아니라 5로 갈아치워야 한다")
    }

    @Test
    fun `changeQuantity로 수량을 깎을 수 있다`() {
        val item = CartItem.of(10L, 5)

        item.changeQuantity(2)

        assertEquals(2, item.quantity, "대입이므로 줄이는 방향도 받아야 한다. accumulate는 음수를 막아 이걸 못 함")
    }

    @Test
    fun `changeQuantity의 경계 1과 99는 통과한다`() {
        val item = CartItem.of(10L, 5)

        item.changeQuantity(CartItem.MIN_QUANTITY)
        assertEquals(1, item.quantity, "하한 경계는 성공이어야 한다")

        item.changeQuantity(CartItem.MAX_QUANTITY)
        assertEquals(99, item.quantity, "상한 경계는 성공이어야 한다")
    }

    @Test
    fun `changeQuantity도 1 미만은 90001이고 수량을 바꾸지 않는다`() {
        val item = CartItem.of(10L, 3)

        val e = assertFailsWith<BusinessException> { item.changeQuantity(0) }

        assertEquals(CommonErrorCode.INVALID_REQUEST, e.errorCode, "대입도 하한 위반은 90001이어야 한다")
        assertEquals(3, item.quantity, "실패한 변경은 수량을 바꾸지 않아야 한다")
    }

    @Test
    fun `changeQuantity도 99 초과는 90001이고 수량을 바꾸지 않는다`() {
        val item = CartItem.of(10L, 3)

        val e = assertFailsWith<BusinessException> { item.changeQuantity(CartItem.MAX_QUANTITY + 1) }

        assertEquals(CommonErrorCode.INVALID_REQUEST, e.errorCode, "대입도 상한 위반은 90001이어야 한다")
        assertEquals(3, item.quantity, "실패한 변경은 수량을 바꾸지 않아야 한다")
    }

    @Test
    fun `changeOption은 조합만 갈아끼우고 수량은 건드리지 않는다`() {
        val item = CartItem.of(10L, 4)

        item.changeOption(11L)

        assertEquals(11L, item.optionCombinationId, "조합이 바뀌어야 한다")
        assertEquals(4, item.quantity, "옵션 변경은 수량을 건드리지 않아야 한다")
    }
}
