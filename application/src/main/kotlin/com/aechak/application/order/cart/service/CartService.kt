package com.aechak.application.order.cart.service

import com.aechak.application.order.cart.port.CartCatalogQueryPort
import com.aechak.application.order.cart.port.view.CartCatalogItemView
import com.aechak.application.order.cart.usecase.command.AddCartItemCommand
import com.aechak.application.order.cart.usecase.result.AddCartItemResult
import com.aechak.common.error.BusinessException
import com.aechak.domain.order.cart.Cart
import com.aechak.domain.order.cart.repository.CartRepository
import com.aechak.domain.order.error.OrderErrorCode
import com.aechak.domain.product.product.enums.SaleStatus
import com.aechak.domain.seller.seller.enums.SellerStatus
import org.springframework.stereotype.Service

@Service
class CartService(
    private val cartRepository: CartRepository,
    private val cartCatalogQueryPort: CartCatalogQueryPort,
) {
    /**
     * 장바구니 행은 파사드가 확보해 둔 상태.
     * 검증 순서가 계약임. 90001과 50203은 cart.addItem이 던지므로 그 호출이 재고 검사보다 앞이어야 함.
     */
    fun addItem(command: AddCartItemCommand): AddCartItemResult {
        val catalogItem =
            cartCatalogQueryPort.findItem(command.optionCombinationId)
                ?: throw BusinessException(OrderErrorCode.CART_ITEM_OPTION_NOT_FOUND)
        if (!purchasable(catalogItem)) {
            throw BusinessException(OrderErrorCode.CART_ITEM_NOT_PURCHASABLE)
        }

        val cart =
            cartRepository.findByBuyerIdForUpdate(command.buyerId)
                ?: throw BusinessException(OrderErrorCode.CART_NOT_FOUND)

        val cartItem = cart.addItem(command.optionCombinationId, command.quantity)
        // 요청 수량이 아니라 누적 결과를 재고와 견줌
        if (cartItem.quantity > catalogItem.stockQuantity) {
            throw BusinessException(OrderErrorCode.CART_ITEM_OUT_OF_STOCK)
        }
        cartRepository.flush()

        return AddCartItemResult.from(cartItem, catalogItem, cart.items.sumOf { it.quantity })
    }

    /**
     * 셀러는 ACTIVE 아닌 값을 전부 막음. 열거하면 상태가 늘 때 구멍이 생김.
     * 판매 상태는 반대로 막을 값만 열거함. 재고 0의 OUT_OF_STOCK은 재고 문제라 50201 몫.
     */
    private fun purchasable(catalogItem: CartCatalogItemView): Boolean =
        catalogItem.sellerStatus == SellerStatus.ACTIVE &&
            catalogItem.saleStatus != SaleStatus.SUSPENDED &&
            catalogItem.saleStatus != SaleStatus.ENDED &&
            catalogItem.optionActive

    fun findCart(buyerId: Long): Cart? = cartRepository.findByBuyerId(buyerId)

    /** UNIQUE(buyer_id) 충돌 예외를 그대로 내보냄. 잡는 쪽은 파사드. */
    fun createCart(buyerId: Long): Cart = cartRepository.save(Cart.create(buyerId))
}
