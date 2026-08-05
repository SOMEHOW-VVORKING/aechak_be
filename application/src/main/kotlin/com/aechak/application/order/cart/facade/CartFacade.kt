package com.aechak.application.order.cart.facade

import com.aechak.application.order.cart.service.CartService
import com.aechak.application.order.cart.usecase.CartUseCase
import com.aechak.application.order.cart.usecase.command.AddCartItemCommand
import com.aechak.application.order.cart.usecase.result.AddCartItemResult
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate

/**
 * 트랜잭션 경계 둘을 파사드가 소유함. 장바구니 확보가 하나, 담기가 하나.
 * 생성이 담기 트랜잭션에 중첩되지 않아야 충돌을 잡고 이어갈 수 있음.
 */
@Service
class CartFacade(
    private val cartService: CartService,
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

    /**
     * 장바구니를 돌려주지 않음. 트랜잭션이 닫히면 준영속이고 담기 쪽이 쓸 행 잠금도 없어 넘겨도 못 씀.
     * try가 tx.execute를 감싸야 함. 안에서 잡으면 롤백 전용 마킹 때문에 커밋에서 다시 터짐.
     */
    private fun ensureCart(buyerId: Long) {
        if (cartService.findCart(buyerId) != null) return
        try {
            tx.execute { cartService.createCart(buyerId) }
        } catch (e: DataIntegrityViolationException) {
            log.debug("장바구니 생성 경합, 기존 행으로 진행함. buyerId={}", buyerId, e)
        }
    }
}
