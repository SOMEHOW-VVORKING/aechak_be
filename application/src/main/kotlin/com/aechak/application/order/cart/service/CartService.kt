package com.aechak.application.order.cart.service

import com.aechak.application.order.cart.port.CartCatalogQueryPort
import com.aechak.application.order.cart.port.view.CartCatalogItemView
import com.aechak.application.order.cart.usecase.command.AddCartItemCommand
import com.aechak.application.order.cart.usecase.result.AddCartItemResult
import com.aechak.common.error.BusinessException
import com.aechak.domain.order.cart.Cart
import com.aechak.domain.order.cart.CartItem
import com.aechak.domain.order.cart.enums.CartItemStatus
import com.aechak.domain.order.cart.repository.CartRepository
import com.aechak.domain.order.error.OrderErrorCode
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class CartService(
    private val cartRepository: CartRepository,
    private val cartCatalogQueryPort: CartCatalogQueryPort,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 장바구니 행은 파사드가 확보해 둔 상태.
     * 검증 순서가 계약임. 90001과 50203은 cart.addItem이 던지므로 그 호출이 재고 검사보다 앞이어야 함.
     */
    fun addItem(command: AddCartItemCommand): AddCartItemResult {
        val catalogItem =
            cartCatalogQueryPort.findItem(command.optionCombinationId)
                ?: throw BusinessException(OrderErrorCode.CART_ITEM_OPTION_NOT_FOUND)
        val itemStatus = catalogItem.itemStatus()
        if (itemStatus != CartItemStatus.ACTIVE && itemStatus != CartItemStatus.OUT_OF_STOCK) {
            throw BusinessException(OrderErrorCode.CART_ITEM_NOT_PURCHASABLE)
        }

        val cart =
            cartRepository.findByBuyerIdForUpdate(command.buyerId)
                ?: error("장바구니가 없다. 파사드가 확보했어야 한다. buyerId=${command.buyerId}")

        val cartItem = cart.addItem(command.optionCombinationId, command.quantity)
        // 요청 수량이 아니라 누적 결과를 재고와 견줌
        if (cartItem.quantity > catalogItem.stockQuantity) {
            throw BusinessException(OrderErrorCode.CART_ITEM_OUT_OF_STOCK)
        }
        cartRepository.flush()

        return AddCartItemResult.from(cartItem, catalogItem, itemStatus, cart.items.sumOf { it.quantity })
    }

    fun cartExists(buyerId: Long): Boolean = cartRepository.existsByBuyerId(buyerId)

    fun findCartItems(buyerId: Long): List<CartItem> = cartRepository.findByBuyerIdWithItems(buyerId)?.items.orEmpty()

    /** 검수 미승인, 카탈로그 행 자체가 없는 것은 제외함. */
    fun findDisplayCatalog(items: List<CartItem>): Map<Long, CartCatalogItemView> {
        if (items.isEmpty()) return emptyMap()

        val fetched =
            cartCatalogQueryPort
                .findItems(items.map { it.optionCombinationId })
                .associateBy { it.optionCombinationId }

        items.filterNot { fetched.containsKey(it.optionCombinationId) }.forEach {
            log.warn("카탈로그 행이 없어 장바구니 항목을 제외함. cartItemId={}, optionCombinationId={}", it.id, it.optionCombinationId)
        }
        return fetched.filterValues { it.approved() }
    }

    /** UNIQUE(buyer_id) 충돌 예외를 그대로 내보냄. 잡는 쪽은 파사드. */
    fun createCart(buyerId: Long) = cartRepository.save(Cart.create(buyerId))
}
