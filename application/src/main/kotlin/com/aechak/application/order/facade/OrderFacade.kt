package com.aechak.application.order.facade

import com.aechak.application.order.cart.service.CartService
import com.aechak.application.order.service.OrderService
import com.aechak.application.order.usecase.OrderUseCase
import com.aechak.application.order.usecase.command.CreateOrderGroupCommand
import com.aechak.application.order.usecase.result.CreateOrderGroupResult
import com.aechak.application.user.address.usecase.DeliveryAddressUseCase
import com.aechak.application.user.point.usecase.PointUseCase
import com.aechak.application.user.point.usecase.command.UsePointCommand
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate

/**
 * OrderUseCase의 유일한 구현체. 트랜잭션 경계는 여기 고정.
 * 타 BC 협력(배송지·적립금)은 그쪽 UseCase를, 같은 BC의 장바구니는 CartService를 주입받는다.
 */
@Service
class OrderFacade(
    private val orderService: OrderService,
    private val cartService: CartService,
    private val deliveryAddressUseCase: DeliveryAddressUseCase,
    private val pointUseCase: PointUseCase,
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
                val result = orderService.createOrderGroup(command, cartItems, address)
                usePoint(command, result)
                result
            }!!
        } catch (e: DataIntegrityViolationException) {
            // 동시 더블클릭은 사전 조회를 둘 다 통과한다. 멱등키 UNIQUE가 심판이고 진 쪽은 최초 결과를 돌려준다.
            // try가 tx.execute를 감싸야 함 — 안에서 잡으면 롤백 전용 마킹 때문에 커밋에서 다시 터짐
            orderService.findByIdempotencyKey(command.idempotencyKey, command.buyerId) ?: throw e
        }
    }

    /** 적립금 확보도 주문 트랜잭션 안 — 실패(잔액 부족)면 재고 차감·주문 저장이 함께 롤백된다 */
    private fun usePoint(
        command: CreateOrderGroupCommand,
        result: CreateOrderGroupResult,
    ) {
        if (command.usedPoint == 0L) return
        pointUseCase.usePoint(
            UsePointCommand(
                userId = command.buyerId,
                amount = command.usedPoint,
                idempotencyKey = "$POINT_USE_KEY_PREFIX${result.orderGroupId}",
                sourceType = POINT_SOURCE_ORDER_GROUP,
            ),
        )
    }

    companion object {
        // 주문그룹 publicId 기반 결정적 키 — 같은 그룹의 사용 기록은 재실행돼도 원장 1행
        const val POINT_USE_KEY_PREFIX = "USE:ORDER:"
        const val POINT_SOURCE_ORDER_GROUP = "ORDER_GROUP"
    }
}
