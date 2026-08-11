package com.aechak.application.order.cart.service

import com.aechak.application.order.cart.port.CartCatalogQueryPort
import com.aechak.application.order.cart.port.view.CartCatalogItemView
import com.aechak.application.order.cart.usecase.command.AddCartItemCommand
import com.aechak.application.order.cart.usecase.command.DeleteCartItemsCommand
import com.aechak.application.order.cart.usecase.command.UpdateCartItemCommand
import com.aechak.application.order.cart.usecase.result.AddCartItemResult
import com.aechak.application.order.cart.usecase.result.DeleteCartItemsResult
import com.aechak.application.order.cart.usecase.result.UpdateCartItemResult
import com.aechak.common.error.BusinessException
import com.aechak.common.error.CommonErrorCode
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
     * 수량 상한과 품목 종류 상한은 cart.addItem이 던지므로 그 호출이 재고 검사보다 앞이어야 함.
     */
    fun addItem(command: AddCartItemCommand): AddCartItemResult {
        val catalogItem =
            cartCatalogQueryPort.findItem(command.optionCombinationId)
                ?: throw BusinessException(OrderErrorCode.CART_ITEM_OPTION_NOT_FOUND)
        val itemStatus = requirePurchasable(catalogItem)

        val cart =
            cartRepository.findByBuyerIdForUpdate(command.buyerId)
                ?: error("장바구니가 없다. 파사드가 확보했어야 한다. buyerId=${command.buyerId}")

        val cartItem = cart.addItem(command.optionCombinationId, command.quantity)
        // 요청 수량이 아니라 누적 결과를 재고와 견줌
        if (cartItem.quantity > catalogItem.stockQuantity) {
            throw BusinessException(OrderErrorCode.CART_ITEM_OUT_OF_STOCK)
        }
        // 응답에 cartItemId를 실기 위함. id 채번을 위함
        cartRepository.flush()

        return AddCartItemResult.from(cartItem, catalogItem, itemStatus)
    }

    /**
     * 담기와 달리 항목 소유권을 먼저 가려야 해서 잠금이 카탈로그 조회보다 앞섬.
     */
    fun updateItem(command: UpdateCartItemCommand): UpdateCartItemResult {
        val requestedQuantity = validatedQuantity(command)

        val cart = cartRepository.findByBuyerIdForUpdate(command.buyerId)
        val item = cart?.findItem(command.cartItemId) ?: throw itemNotFoundOrDenied(command.cartItemId)

        val targetOptionCombinationId = command.optionCombinationId?.takeIf { it != item.optionCombinationId }
        val newQuantity = requestedQuantity ?: item.quantity

        // 수량을 줄이거나 그대로 두고 옵션도 안 바꾸면 늘어나는 게 없어서 카탈로그를 아예 조회하지 않음. 수량 변경 자체는 아래에서 그대로 적용
        val catalog =
            if (targetOptionCombinationId == null && newQuantity <= item.quantity) {
                emptyMap()
            } else {
                verifiedCatalog(item.optionCombinationId, targetOptionCombinationId)
            }

        item.changeQuantity(newQuantity)
        val updatedItem = targetOptionCombinationId?.let { cart.changeItemOption(item, it) } ?: item

        requireEnoughStock(updatedItem, catalog)

        return UpdateCartItemResult.from(
            updatedItem = updatedItem,
            merged = updatedItem.id != item.id,
        )
    }

    fun deleteItems(command: DeleteCartItemsCommand): DeleteCartItemsResult {
        val cart =
            cartRepository.findByBuyerIdForUpdate(command.buyerId)
                ?: return DeleteCartItemsResult(deletedCount = 0)

        val ignoredIds = command.cartItemIds - cart.getItemIds()
        if (ignoredIds.isNotEmpty()) {
            log.warn("내 장바구니에 없는 항목의 삭제 요청을 무시함. buyerId={}, cartItemIds={}", command.buyerId, ignoredIds)
        }

        return DeleteCartItemsResult(deletedCount = cart.removeItems(command.cartItemIds))
    }

    private fun validatedQuantity(command: UpdateCartItemCommand): Int? {
        if (command.quantity == null && command.optionCombinationId == null) {
            throw BusinessException(CommonErrorCode.INVALID_REQUEST)
        }
        command.quantity?.let {
            if (it < CartItem.MIN_QUANTITY || it > CartItem.MAX_QUANTITY) {
                throw BusinessException(CommonErrorCode.INVALID_REQUEST)
            }
        }
        return command.quantity
    }

    private fun itemNotFoundOrDenied(cartItemId: Long): BusinessException =
        if (cartRepository.existsAnyItemById(setOf(cartItemId))) {
            BusinessException(OrderErrorCode.CART_ITEM_ACCESS_DENIED)
        } else {
            BusinessException(OrderErrorCode.CART_ITEM_NOT_FOUND)
        }

    private fun verifiedCatalog(
        currentOptionCombinationId: Long,
        targetOptionCombinationId: Long?,
    ): Map<Long, CartCatalogItemView> {
        val catalog =
            cartCatalogQueryPort
                .findApprovedItems(setOfNotNull(currentOptionCombinationId, targetOptionCombinationId))
                .associateBy { it.optionCombinationId }

        val current = catalog[currentOptionCombinationId] ?: throw BusinessException(OrderErrorCode.CART_ITEM_OPTION_NOT_FOUND)
        if (targetOptionCombinationId == null) {
            requirePurchasable(current)
            return catalog
        }

        val target = catalog[targetOptionCombinationId] ?: throw BusinessException(OrderErrorCode.CART_ITEM_OPTION_NOT_FOUND)
        if (target.productPublicId != current.productPublicId) {
            throw BusinessException(CommonErrorCode.INVALID_REQUEST)
        }
        // 떠나는 조합은 안 봄. 죽은 조합에서 산 조합으로 갈아타는 길을 막지 않기 위함
        requirePurchasable(target)
        return catalog
    }

    private fun requireEnoughStock(
        item: CartItem,
        catalog: Map<Long, CartCatalogItemView>,
    ) {
        val stockQuantity = catalog[item.optionCombinationId]?.stockQuantity ?: return
        if (item.quantity > stockQuantity) {
            throw BusinessException(OrderErrorCode.CART_ITEM_OUT_OF_STOCK)
        }
    }

    /** 담기와 같은 판정. 품절은 여기서 안 막고 재고 검사가 가름 */
    private fun requirePurchasable(catalogItem: CartCatalogItemView): CartItemStatus {
        val itemStatus = catalogItem.itemStatus()
        if (itemStatus != CartItemStatus.ACTIVE && itemStatus != CartItemStatus.OUT_OF_STOCK) {
            throw BusinessException(OrderErrorCode.CART_ITEM_NOT_PURCHASABLE)
        }
        return itemStatus
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
