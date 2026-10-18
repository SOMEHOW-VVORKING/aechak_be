package com.aechak.application.order.facade

import com.aechak.application.order.cart.service.CartService
import com.aechak.application.order.port.OrderItemForReviewQueryPort
import com.aechak.application.order.service.OrderExpiryService
import com.aechak.application.order.service.OrderService
import com.aechak.application.order.support.OrderPointLedger
import com.aechak.application.order.usecase.OrderExpiryUseCase
import com.aechak.application.order.usecase.OrderUseCase
import com.aechak.application.order.usecase.command.CreateOrderGroupCommand
import com.aechak.application.order.usecase.command.ExpireOrderGroupCommand
import com.aechak.application.order.usecase.result.ConfirmGroupPaidResult
import com.aechak.application.order.usecase.result.CreateOrderGroupResult
import com.aechak.application.order.usecase.result.ExpireOrderGroupResult
import com.aechak.application.order.usecase.result.ExpiredOrderGroupResult
import com.aechak.application.order.usecase.result.OrderItemForReviewResult
import com.aechak.application.user.address.usecase.DeliveryAddressUseCase
import com.aechak.application.user.point.usecase.PointUseCase
import com.aechak.application.user.point.usecase.command.UsePointCommand
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.time.LocalDateTime

/**
 * order 도메인 진입점의 유일한 구현체. 트랜잭션 경계는 여기 고정.
 * 타 BC 협력(배송지·적립금)은 그쪽 UseCase를, 같은 BC의 장바구니는 CartService를 주입받는다.
 * 만료 계약을 인터페이스로 가른 이유는 소비 모듈이 갈려서다 — 구매자 앱은 쓰지 않고 배치만 쓴다.
 */
@Service
class OrderFacade(
    private val orderService: OrderService,
    private val orderExpiryService: OrderExpiryService,
    private val cartService: CartService,
    private val deliveryAddressUseCase: DeliveryAddressUseCase,
    private val pointUseCase: PointUseCase,
    private val orderItemForReviewQueryPort: OrderItemForReviewQueryPort,
    transactionManager: PlatformTransactionManager,
) : OrderUseCase,
    OrderExpiryUseCase {
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

    @Transactional(readOnly = true)
    override fun getOrderItemForReview(
        orderItemId: Long,
        buyerId: Long,
    ): OrderItemForReviewResult? =
        orderItemForReviewQueryPort.findOrderItemForReview(orderItemId, buyerId)?.let(OrderItemForReviewResult::from)

    /** 결제 확정 트랜잭션에 참여해야 하므로 REQUIRED — 결제 기록이 롤백되면 주문 전이도 함께 롤백된다 */
    @Transactional
    override fun confirmGroupPaid(orderGroupId: Long): ConfirmGroupPaidResult = orderService.confirmGroupPaid(orderGroupId)

    @Transactional(readOnly = true)
    override fun findExpiredOrderGroups(limit: Int): List<ExpiredOrderGroupResult> =
        orderExpiryService.findExpired(LocalDateTime.now(), limit)

    /** 취소 전이·재고·적립금이 한 트랜잭션 — 복원 중 하나라도 터지면 그룹이 결제대기로 돌아가 다음 회차가 다시 집는다 */
    @Transactional
    override fun expireOrderGroup(command: ExpireOrderGroupCommand): ExpireOrderGroupResult =
        orderExpiryService.expire(command.orderGroupId, LocalDateTime.now())

    @Transactional
    override fun clearOrderedCartItems(
        buyerId: Long,
        orderGroupId: Long,
    ): Int = cartService.removeOrderedItems(buyerId, orderService.orderedQuantities(orderGroupId))

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
                idempotencyKey = OrderPointLedger.useKey(result.orderGroupId),
                sourceType = OrderPointLedger.SOURCE_TYPE,
            ),
        )
    }
}
