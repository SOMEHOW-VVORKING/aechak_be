package com.aechak.application.order.facade

import com.aechak.application.messaging.MessagePublisher
import com.aechak.application.order.cart.service.CartService
import com.aechak.application.order.service.OrderGroupService
import com.aechak.application.order.service.OrderService
import com.aechak.application.order.service.model.CancelledOrderGroup
import com.aechak.application.order.support.OrderPointKeys
import com.aechak.application.order.usecase.OrderGroupExpireUseCase
import com.aechak.application.order.usecase.OrderUseCase
import com.aechak.application.order.usecase.command.CreateOrderGroupCommand
import com.aechak.application.order.usecase.result.CreateOrderGroupResult
import com.aechak.application.order.usecase.result.ExpireTargetResult
import com.aechak.application.payment.port.PaymentGatewayPort
import com.aechak.application.user.address.usecase.DeliveryAddressUseCase
import com.aechak.application.user.point.usecase.PointUseCase
import com.aechak.application.user.point.usecase.command.UsePointCommand
import com.aechak.domain.order.group.repository.ExpiredPendingOrderGroup
import com.aechak.message.order.OrderGroupCancelledMessage
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate
import java.time.LocalDateTime

/**
 * OrderUseCase·OrderGroupExpireUseCase의 유일한 구현체. 트랜잭션 경계는 여기 고정.
 * 타 BC 협력(배송지·적립금)은 그쪽 UseCase를, 같은 BC의 장바구니는 CartService를 주입받는다.
 */
@Service
class OrderFacade(
    private val orderService: OrderService,
    private val orderGroupService: OrderGroupService,
    private val cartService: CartService,
    private val deliveryAddressUseCase: DeliveryAddressUseCase,
    private val pointUseCase: PointUseCase,
    private val paymentGateway: PaymentGatewayPort,
    private val messagePublisher: MessagePublisher,
    transactionManager: PlatformTransactionManager,
) : OrderUseCase,
    OrderGroupExpireUseCase {
    private val log = LoggerFactory.getLogger(javaClass)

    private val tx =
        TransactionTemplate(transactionManager).apply {
            isolationLevel = TransactionDefinition.ISOLATION_READ_COMMITTED
        }

    /** 만료는 청크 내에서 건 별로 격리하기 위함 */
    private val orderGroupExpireTx =
        TransactionTemplate(transactionManager).apply {
            isolationLevel = TransactionDefinition.ISOLATION_READ_COMMITTED
            propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
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

    override fun findExpireTargets(
        after: ExpireTargetResult?,
        limit: Int,
    ): List<ExpireTargetResult> =
        orderGroupService
            .findExpiredPendingTargets(LocalDateTime.now(), after?.toExpiredPendingOrderGroup(), limit)
            .map { ExpireTargetResult(it.id, it.publicId, it.expiresAt) }

    override fun cancelIfUnpaid(target: ExpireTargetResult) {
        val gatewayView = paymentGateway.find(target.orderGroupPublicId)
        if (orderGroupService.isUnpaid(target.orderGroupPublicId, gatewayView)) {
            orderGroupExpireTx.execute { cancelAndPublish(target.orderGroupId) }
        }
    }

    private fun ExpireTargetResult.toExpiredPendingOrderGroup() = ExpiredPendingOrderGroup(orderGroupId, orderGroupPublicId, expiresAt)

    /** 재고와 적립금 복원은 이벤트로 처리 */
    private fun cancelAndPublish(orderGroupId: Long) {
        val cancelled = orderGroupService.cancelUnpaidGroup(orderGroupId)
        if (cancelled == null) {
            log.info("다른 쪽이 먼저 전이해 만료 취소를 건너뜀. orderGroupId={}", orderGroupId)
            return
        }
        messagePublisher.publish(cancelled.toOrderGroupCancelledMessage())
    }

    private fun CancelledOrderGroup.toOrderGroupCancelledMessage() =
        OrderGroupCancelledMessage(
            orderGroupPublicId = publicId,
            buyerId = buyerId,
            usedPoint = usedPoint,
            items = items.map { OrderGroupCancelledMessage.Item(it.optionCombinationId, it.quantity) },
        )

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
                idempotencyKey = OrderPointKeys.useKey(result.orderGroupId),
                sourceType = OrderPointKeys.SOURCE_TYPE_ORDER_GROUP,
            ),
        )
    }
}
