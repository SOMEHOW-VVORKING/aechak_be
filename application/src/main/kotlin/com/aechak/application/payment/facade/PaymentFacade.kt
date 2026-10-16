package com.aechak.application.payment.facade

import com.aechak.application.order.usecase.OrderUseCase
import com.aechak.application.order.usecase.result.ConfirmGroupPaidResult
import com.aechak.application.payment.port.PaymentGatewayPort
import com.aechak.application.payment.port.PaymentGatewayStatus
import com.aechak.application.payment.port.PaymentGatewayView
import com.aechak.application.payment.service.PaymentService
import com.aechak.application.payment.usecase.PaymentUseCase
import com.aechak.application.payment.usecase.command.CompletePaymentCommand
import com.aechak.application.payment.usecase.command.PreparePaymentCommand
import com.aechak.application.payment.usecase.result.CompletePaymentResult
import com.aechak.application.payment.usecase.result.CompletePaymentStatus
import com.aechak.application.payment.usecase.result.PreparePaymentResult
import com.aechak.common.error.BusinessException
import com.aechak.domain.order.group.OrderGroup
import com.aechak.domain.order.group.enums.OrderGroupStatus
import com.aechak.domain.payment.Payment
import com.aechak.domain.payment.error.PaymentErrorCode
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

/**
 * 확정 로직의 단일 진입점 — 콜백(이 EP)·웹훅·만료 배치가 전부 이 흐름을 태운다.
 * 진실은 포트원에 있으므로 조회는 트랜잭션 밖, 전이는 트랜잭션 안(주문그룹 선점이 심판).
 */
@Service
class PaymentFacade(
    private val paymentService: PaymentService,
    private val paymentGateway: PaymentGatewayPort,
    private val orderUseCase: OrderUseCase,
    transactionManager: PlatformTransactionManager,
) : PaymentUseCase {
    private val log = LoggerFactory.getLogger(javaClass)
    private val tx = TransactionTemplate(transactionManager)

    override fun preparePayment(command: PreparePaymentCommand): PreparePaymentResult {
        val payment =
            try {
                tx.execute { paymentService.prepare(command) }!!
            } catch (e: DataIntegrityViolationException) {
                tx.execute { paymentService.prepare(command) } ?: throw e // 동시 요청이 먼저 만든 행이라 다시 조회해 돌려줌
            }
        paymentGateway.preRegister(payment.paymentId, payment.targetAmount)
        return PreparePaymentResult.from(payment)
    }

    override fun completePayment(command: CompletePaymentCommand): CompletePaymentResult {
        val target = paymentService.loadCompletionTarget(command)
        // 1단계: 포트원에 물어보지 않아도 답이 정해지는 경우를 먼저 끝낸다
        completeWithoutGateway(target)?.let { return it }
        val payment = requireNotNull(target.payment) { "1단계를 지나왔으면 결제 행이 있어야 합니다 (orderGroupPublicId=${target.group.publicId})" }
        // 2단계: 결제가 실제로 어떻게 됐는지는 포트원만 안다 — 물어보고(트랜잭션 밖) 대답대로 처리한다
        val view = paymentGateway.find(payment.paymentId)
        return completeByGatewayStatus(command, target.group, payment, view)
    }

    /**
     * 우리 DB만 보고 답할 수 있는 경우의 처리.
     * null = "결제를 시도한 흔적은 있는데 결과는 모르는 상태" — 이때만 포트원에 묻는다.
     */
    private fun completeWithoutGateway(target: PaymentService.CompletionTarget): CompletePaymentResult? {
        val group = target.group
        return when (group.status) {
            // 이미 확정된 그룹 — 콜백 재호출·웹훅 경쟁의 진 쪽이라 결과만 멱등 재반환
            OrderGroupStatus.PAID -> {
                CompletePaymentResult.of(CompletePaymentStatus.PAID, group)
            }

            OrderGroupStatus.CANCELLED -> {
                throw BusinessException(PaymentErrorCode.PAYMENT_ORDER_GROUP_CANCELLED)
            }

            // 부분취소는 결제 완료 이후에만 생기는 상태 — 아직 생산자가 없고, 클레임 구현 시 이 분기를 재정의한다
            OrderGroupStatus.PARTIALLY_CANCELLED -> {
                throw BusinessException(PaymentErrorCode.PAYMENT_ORDER_GROUP_CANCELLED)
            }

            OrderGroupStatus.PENDING_PAYMENT -> {
                // 결제 행이 없다 = 결제창을 열 준비(prepare)조차 안 했다 — 포트원도 모르는 건이라 물어볼 것이 없다
                if (target.payment == null) CompletePaymentResult.of(CompletePaymentStatus.NOT_STARTED, group) else null
            }
        }
    }

    /**
     * 포트원 대답별 처리 — 전이가 있는 대답은 PAID(확정)와 FAILED(기록)뿐이다.
     * 결제를 새로 일으키는 건 서버가 할 수 없는 일이다(승인은 사용자가 결제창에서만) —
     * 미완료 대답이면 상태만 알려주고, 재시도는 FE가 prepare부터 다시 밟는다.
     */
    private fun completeByGatewayStatus(
        command: CompletePaymentCommand,
        group: OrderGroup,
        payment: Payment,
        view: PaymentGatewayView?,
    ): CompletePaymentResult =
        when (view?.status) {
            null, PaymentGatewayStatus.READY -> {
                CompletePaymentResult.of(CompletePaymentStatus.NOT_STARTED, group)
            }

            // 승인 대기는 포트원 문서상 페이팔 전용 상태 — 현 결제수단(카드·간편)에선 오지 않는 방어 분기
            PaymentGatewayStatus.PAY_PENDING -> {
                CompletePaymentResult.of(CompletePaymentStatus.IN_PROGRESS, group)
            }

            PaymentGatewayStatus.PAID -> {
                completeAsPaid(command, group, payment, view)
            }

            PaymentGatewayStatus.FAILED -> {
                tx.execute {
                    CompletePaymentResult.of(CompletePaymentStatus.FAILED, group, paymentService.markFailed(payment, view))
                }!!
            }

            else -> {
                throw BusinessException(PaymentErrorCode.PAYMENT_STATE_NOT_SUPPORTED)
            }
        }

    /** "승인됐다"는 대답의 마무리 — 금액 대조를 통과하면 한 트랜잭션에서 주문·결제를 결제완료로 만들고, 커밋 뒤 장바구니를 정리한다 */
    private fun completeAsPaid(
        command: CompletePaymentCommand,
        group: OrderGroup,
        payment: Payment,
        view: PaymentGatewayView,
    ): CompletePaymentResult {
        paymentService.assertAmountsMatch(group, payment, view)
        val result =
            tx.execute {
                when (orderUseCase.confirmGroupPaid(group.id)) {
                    ConfirmGroupPaidResult.CONFIRMED -> {
                        CompletePaymentResult.of(CompletePaymentStatus.PAID, group, paymentService.approve(payment, view))
                    }

                    ConfirmGroupPaidResult.ALREADY_PAID -> {
                        // 다른 입구가 승인 기록까지 마치고 커밋한 뒤에만 선점에 지므로, 기록을 다시 만들지 않는다
                        CompletePaymentResult.of(CompletePaymentStatus.PAID, group, paymentService.getByOrderGroupId(group.id))
                    }

                    ConfirmGroupPaidResult.ALREADY_CANCELLED -> {
                        throw BusinessException(PaymentErrorCode.PAYMENT_ORDER_GROUP_CANCELLED)
                    }
                }
            }!!
        clearCart(command.buyerId, group)
        return result
    }

    /** 확정 커밋 뒤 별도 트랜잭션 — 실패는 구매자가 직접 지우면 되는 불편이라 보상하지 않는다 */
    private fun clearCart(
        buyerId: Long,
        group: OrderGroup,
    ) {
        runCatching { orderUseCase.clearOrderedCartItems(buyerId, group.id) }
            .onFailure { log.warn("확정 후 장바구니 정리 실패 — 보상 없음. orderGroupPublicId={}", group.publicId, it) }
    }
}
