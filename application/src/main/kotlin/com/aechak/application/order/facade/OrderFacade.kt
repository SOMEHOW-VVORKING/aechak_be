package com.aechak.application.order.facade

import com.aechak.application.order.cart.service.CartService
import com.aechak.application.order.service.OrderService
import com.aechak.application.order.usecase.OrderUseCase
import com.aechak.application.order.usecase.command.CreateOrderGroupCommand
import com.aechak.application.order.usecase.result.ConfirmGroupPaidResult
import com.aechak.application.order.usecase.result.CreateOrderGroupResult
import com.aechak.application.user.address.usecase.DeliveryAddressUseCase
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate

/**
 * OrderUseCase의 유일한 구현체. 트랜잭션 경계는 여기 고정.
 * 타 BC 협력(배송지)은 그쪽 UseCase를, 같은 BC의 장바구니는 CartService를 주입받는다.
 */
@Service
class OrderFacade(
    private val orderService: OrderService,
    private val cartService: CartService,
    private val deliveryAddressUseCase: DeliveryAddressUseCase,
    transactionManager: PlatformTransactionManager,
) : OrderUseCase {
    private val tx =
        TransactionTemplate(transactionManager).apply {
            isolationLevel = TransactionDefinition.ISOLATION_READ_COMMITTED
        }

    override fun createOrderGroup(command: CreateOrderGroupCommand): CreateOrderGroupResult {
        orderService.findByIdempotencyKey(command.idempotencyKey, command.buyerId)?.let { return it }
        val address = deliveryAddressUseCase.getDeliveryAddress(command.buyerId, command.deliveryAddressId)
        return try {
            tx.execute {
                val cartItems = cartService.findCartItems(command.buyerId)
                orderService.createOrderGroup(command, cartItems, address)
            }!!
        } catch (e: DataIntegrityViolationException) {
            // 동시 더블클릭은 사전 조회를 둘 다 통과한다. 멱등키 UNIQUE가 심판이고 진 쪽은 최초 결과를 돌려준다.
            // try가 tx.execute를 감싸야 함 — 안에서 잡으면 롤백 전용 마킹 때문에 커밋에서 다시 터짐
            orderService.findByIdempotencyKey(command.idempotencyKey, command.buyerId) ?: throw e
        }
    }

    /** 결제 확정 트랜잭션에 참여해야 하므로 REQUIRED — 결제 기록이 롤백되면 주문 전이도 함께 롤백된다 */
    @Transactional
    override fun confirmGroupPaid(orderGroupId: Long): ConfirmGroupPaidResult = orderService.confirmGroupPaid(orderGroupId)

    @Transactional
    override fun clearOrderedCartItems(
        buyerId: Long,
        orderGroupId: Long,
    ): Int = cartService.removeOrderedItems(buyerId, orderService.orderedQuantities(orderGroupId))
}
