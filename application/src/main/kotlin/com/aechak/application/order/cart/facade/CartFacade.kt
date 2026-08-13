package com.aechak.application.order.cart.facade

import com.aechak.application.file.usecase.FileUseCase
import com.aechak.application.order.cart.service.CartService
import com.aechak.application.order.cart.usecase.CartUseCase
import com.aechak.application.order.cart.usecase.command.AddCartItemCommand
import com.aechak.application.order.cart.usecase.command.DeleteCartItemsCommand
import com.aechak.application.order.cart.usecase.command.UpdateCartItemCommand
import com.aechak.application.order.cart.usecase.result.AddCartItemResult
import com.aechak.application.order.cart.usecase.result.CartItemCountResult
import com.aechak.application.order.cart.usecase.result.CartResult
import com.aechak.application.order.cart.usecase.result.DeleteCartItemsResult
import com.aechak.application.order.cart.usecase.result.UpdateCartItemResult
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.time.LocalDateTime

/**
 * 담기의 트랜잭션 경계 둘을 파사드가 소유함. 장바구니 확보가 하나, 담기가 하나.
 * 생성이 담기 트랜잭션에 중첩되지 않아야 충돌이 담기까지 굴리지 않음.
 */
@Service
class CartFacade(
    private val cartService: CartService,
    private val fileUseCase: FileUseCase,
    transactionManager: PlatformTransactionManager,
) : CartUseCase {
    private val log = LoggerFactory.getLogger(javaClass)

    // REPEATABLE READ에서는 행 잠금을 잡아도 라인 조회가 트랜잭션 스냅샷을 봐서 누적이 유실됨
    private val tx =
        TransactionTemplate(transactionManager).apply {
            isolationLevel = TransactionDefinition.ISOLATION_READ_COMMITTED
        }

    override fun addCartItem(command: AddCartItemCommand): AddCartItemResult {
        ensureCart(command.buyerId)
        return tx.execute { cartService.addItem(command) }!!
    }

    @Transactional(readOnly = true)
    override fun getCart(buyerId: Long): CartResult {
        val items = cartService.findCartItems(buyerId)
        return CartResult.from(
            items = items,
            catalog = cartService.findDisplayCatalog(items),
            now = LocalDateTime.now(),
            resolveThumbnail = fileUseCase::resolveMediaUrl,
        )
    }

    @Transactional(readOnly = true)
    override fun countCartItems(buyerId: Long): CartItemCountResult = CartItemCountResult(cartService.countDisplayableItems(buyerId))

    @Transactional(isolation = Isolation.READ_COMMITTED)
    override fun updateCartItem(command: UpdateCartItemCommand): UpdateCartItemResult = cartService.updateItem(command)

    @Transactional(isolation = Isolation.READ_COMMITTED)
    override fun deleteCartItems(command: DeleteCartItemsCommand): DeleteCartItemsResult = cartService.deleteItems(command)

    /**
     * try가 tx.execute를 감싸야 함. 안에서 잡으면 롤백 전용 마킹 때문에 커밋에서 다시 터짐.
     */
    private fun ensureCart(buyerId: Long) {
        if (cartService.cartExists(buyerId)) return
        try {
            tx.execute { cartService.createCart(buyerId) }
        } catch (e: DataIntegrityViolationException) {
            if (!cartService.cartExists(buyerId)) throw e
            log.debug("장바구니 생성 경합, 기존 행으로 진행함. buyerId={}", buyerId, e)
        }
    }
}
