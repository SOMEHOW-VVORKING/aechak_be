package com.aechak.domain.order.cart

import com.aechak.common.error.BusinessException
import com.aechak.common.error.CommonErrorCode
import com.aechak.domain.order.error.OrderErrorCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

/** 계약 테스트. 깨지면 재담기가 새 행을 만들거나 옵션 변경 병합이 어긋나거나 품목 종류 상한이 뚫린 것임. */
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
    fun `안 담긴 조합으로 바꾸면 같은 줄의 조합만 갈아끼운다`() {
        val cart = cart()
        val item = cart.addItem(10L, 2)

        val survivor = cart.changeItemOption(item, 11L)

        assertSame(item, survivor, "병합이 아니면 원본 줄이 그대로 살아남아야 한다")
        assertEquals(11L, item.optionCombinationId, "조합이 갈아끼워져야 한다. 삭제 후 재생성이면 다른 인스턴스가 온다")
        assertEquals(2, item.quantity, "옵션만 바꾸면 수량은 그대로여야 한다")
        assertEquals(1, cart.items.size, "라인 수가 변하면 안 된다")
    }

    @Test
    fun `이미 담긴 조합으로 바꾸면 목적지에 합쳐지고 원본 줄이 빠진다`() {
        val cart = cart()
        val destination = cart.addItem(10L, 2)
        val source = cart.addItem(11L, 3)

        val survivor = cart.changeItemOption(source, 10L)

        assertSame(destination, survivor, "살아남는 줄은 목적지여야 한다")
        assertEquals(5, destination.quantity, "목적지 2에 원본 3이 더해져 5여야 한다")
        assertEquals(1, cart.items.size, "원본 줄이 빠져 라인이 하나여야 한다")
        assertEquals(10L, cart.items[0].optionCombinationId, "남은 줄은 목적지 조합이어야 한다")
    }

    @Test
    fun `원본 줄의 조합은 병합 중에도 목적지 값으로 덮이지 않는다`() {
        val cart = cart()
        cart.addItem(10L, 2)
        val source = cart.addItem(11L, 3)

        cart.changeItemOption(source, 10L)

        assertEquals(11L, source.optionCombinationId, "빠진 줄을 목적지 값으로 덮으면 UNIQUE 정합을 하이버네이트 동작에 기대게 된다")
    }

    @Test
    fun `자기 조합으로 바꾸면 병합되지 않고 수량이 그대로다`() {
        val cart = cart()
        val item = cart.addItem(10L, 3)

        val survivor = cart.changeItemOption(item, 10L)

        assertSame(item, survivor, "자기 자신은 병합 대상에서 빠져야 한다")
        assertEquals(3, item.quantity, "자기 자신과 합치면 수량이 두 배가 된다")
        assertEquals(1, cart.items.size, "라인이 사라지면 안 된다")
    }

    @Test
    fun `병합 합산이 99를 넘으면 90001이고 아무것도 안 바뀐다`() {
        val cart = cart()
        val destination = cart.addItem(10L, 60)
        val source = cart.addItem(11L, 50)

        val e = assertFailsWith<BusinessException> { cart.changeItemOption(source, 10L) }

        assertEquals(CommonErrorCode.INVALID_REQUEST, e.errorCode, "병합 합산 상한 초과는 90001이어야 한다")
        assertEquals(2, cart.items.size, "차단된 병합은 원본 줄을 지우면 안 된다")
        assertEquals(60, destination.quantity, "차단된 병합은 목적지 수량을 바꾸면 안 된다")
    }

    @Test
    fun `옵션 변경은 품목 종류 상한을 보지 않는다`() {
        val cart = cart()
        repeat(Cart.MAX_ITEM_KINDS) { cart.addItem(it + 1L, 1) }
        val item = cart.items.first()

        cart.changeItemOption(item, 999L)

        assertEquals(999L, item.optionCombinationId, "종류가 안 늘어나므로 상한에 걸리면 안 된다")
        assertEquals(Cart.MAX_ITEM_KINDS, cart.items.size, "단순 변경은 라인 수를 그대로 둔다")
    }
}
