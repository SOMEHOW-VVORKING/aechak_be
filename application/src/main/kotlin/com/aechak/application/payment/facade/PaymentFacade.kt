package com.aechak.application.payment.facade

import com.aechak.application.order.usecase.OrderUseCase
import com.aechak.application.order.usecase.result.ConfirmGroupPaidResult
import com.aechak.application.payment.port.PaymentCancelCommand
import com.aechak.application.payment.port.PaymentCancelStatus
import com.aechak.application.payment.port.PaymentGatewayPort
import com.aechak.application.payment.port.PaymentGatewayStatus
import com.aechak.application.payment.port.PaymentGatewayView
import com.aechak.application.payment.port.PaymentWebhookNotificationType
import com.aechak.application.payment.port.PaymentWebhookRequest
import com.aechak.application.payment.port.PaymentWebhookVerifier
import com.aechak.application.payment.service.PaymentService
import com.aechak.application.payment.usecase.PaymentUseCase
import com.aechak.application.payment.usecase.command.CancelPaymentCommand
import com.aechak.application.payment.usecase.command.CompletePaymentByGatewayCommand
import com.aechak.application.payment.usecase.command.CompletePaymentByWebhookCommand
import com.aechak.application.payment.usecase.command.CompletePaymentCommand
import com.aechak.application.payment.usecase.command.PreparePaymentCommand
import com.aechak.application.payment.usecase.result.CancelPaymentResult
import com.aechak.application.payment.usecase.result.CancelPaymentStatus
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
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate

/**
 * 확정 로직의 단일 진입점 — 콜백·웹훅·만료 배치가 전부 이 흐름을 태운다.
 * 진실은 포트원에 있으므로 조회는 트랜잭션 밖, 전이는 트랜잭션 안(주문그룹 선점이 심판).
 */
@Service
class PaymentFacade(
    private val paymentService: PaymentService,
    private val paymentGateway: PaymentGatewayPort,
    private val webhookVerifier: PaymentWebhookVerifier,
    private val orderUseCase: OrderUseCase,
    transactionManager: PlatformTransactionManager,
) : PaymentUseCase {
    private val log = LoggerFactory.getLogger(javaClass)

    /** 선점에 진 쪽의 후속 조회가 승자 커밋을 봐야 해서 문장마다 최신 스냅샷을 읽는다 */
    private val tx =
        TransactionTemplate(transactionManager).apply {
            isolationLevel = TransactionDefinition.ISOLATION_READ_COMMITTED
        }

    /**
     * 취소 전용 경계. 호출자가 자기 트랜잭션 안에서 이 포트를 불러도 선점 커밋이 PG 호출보다 먼저 끝나야 하므로
     * 바깥 트랜잭션에 합류하지 않는다. 합류하면 선점이 커밋되기 전에 PG가 호출되고, 호출자 롤백이
     * "PG는 취소했는데 우리 원장은 승인" 상태를 만든다.
     */
    private val cancelTx =
        TransactionTemplate(transactionManager).apply {
            isolationLevel = TransactionDefinition.ISOLATION_READ_COMMITTED
            propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
        }

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

    override fun completePayment(command: CompletePaymentCommand): CompletePaymentResult =
        complete(paymentService.loadCompletionTarget(command), command.buyerId)

    override fun completePaymentByWebhook(command: CompletePaymentByWebhookCommand): CompletePaymentResult? {
        val notification =
            webhookVerifier.verify(
                PaymentWebhookRequest(
                    rawBody = command.rawBody,
                    id = command.webhookId,
                    timestamp = command.webhookTimestamp,
                    signature = command.webhookSignature,
                ),
            ) ?: return null
        val target = paymentService.findCompletionTargetByPaymentId(notification.paymentId)
        if (target == null) {
            log.warn("모르는 결제의 웹훅이라 무시함. paymentId={}, type={}", notification.paymentId, notification.type)
            return null
        }
        log.info("결제 웹훅 수신. paymentId={}, type={}", notification.paymentId, notification.type)
        if (notification.type == PaymentWebhookNotificationType.PAID && target.group.status == OrderGroupStatus.CANCELLED) {
            // 취소 커밋 뒤 뒤늦게 승인된 흔한 순서 — 아래 확정 본체는 조회 없이 접으므로 여기서만 잡을 수 있다
            log.error(
                "취소된 주문그룹에 승인 통보가 도착 — 환불 필요 여부 확인 요망. orderGroupPublicId={}, paymentId={}",
                target.group.publicId,
                notification.paymentId,
            )
        }
        // 구매자는 그룹에서 읽는다 — 확정 뒤 장바구니 정리가 주인을 알아야 하기 때문이다
        return complete(target, target.group.buyerId)
    }

    override fun completePaymentByGateway(command: CompletePaymentByGatewayCommand): CompletePaymentResult? {
        val target = paymentService.findCompletionTargetByPaymentId(command.paymentId) ?: return null
        return complete(target, target.group.buyerId)
    }

    override fun cancelPayment(command: CancelPaymentCommand): CancelPaymentResult {
        // 1. 취소 선점을 먼저 커밋한다 — 이 1행 성공이 "이 취소는 내 것"의 유일한 근거다
        val target =
            try {
                cancelTx.execute { paymentService.requestCancel(command.orderGroupId, command.amount) }!!
            } catch (e: OptimisticLockingFailureException) {
                // 같은 결제를 두 요청이 동시에 선점했다 — 진 쪽은 진행 중인 취소를 기다린다
                throw BusinessException(PaymentErrorCode.PAYMENT_CANCEL_IN_PROGRESS, e)
            }
        val payment = target.payment

        // 2. PG 호출은 트랜잭션 밖. 실패하면 선점을 풀어 재시도 경로를 남긴다 —
        //    PG가 실제로는 취소에 성공한 경우(응답 유실)의 중복 취소는 취소가능액 대조가 막는다
        val cancellation =
            try {
                paymentGateway.cancel(
                    PaymentCancelCommand(
                        paymentId = payment.paymentId,
                        reason = command.reason,
                        amount = if (target.isFullCancel) null else target.requestedAmount,
                        currentCancellableAmount = target.cancellableBefore,
                        requester = command.requester,
                    ),
                )
            } catch (e: RuntimeException) {
                releaseCancelRequest(command.orderGroupId)
                throw e
            }

        // 3. PG가 확정한 금액만 원장에서 뺀다
        return when (cancellation.status) {
            PaymentCancelStatus.SUCCEEDED -> {
                // PG가 확정한 금액만 반영한다 — 없으면 요청액으로 추정하지 않고, 선점을 남겨 재요청을 막고 대사로 넘긴다
                val cancelled =
                    cancellation.cancelledAmount ?: run {
                        log.error(
                            "PG가 취소 확정 금액 없이 성공을 보고함 — 원장 반영 보류, 선점 유지. paymentId={}, requestedAmount={}",
                            payment.paymentId,
                            target.requestedAmount,
                        )
                        throw BusinessException(PaymentErrorCode.PAYMENT_GATEWAY_ERROR)
                    }
                cancelTx.execute {
                    // 밖에서 로딩한 결제 행은 낡았을 수 있어 저장 직전에 다시 읽는다
                    val current = paymentService.getByOrderGroupId(command.orderGroupId)
                    CancelPaymentResult.of(
                        CancelPaymentStatus.COMPLETED,
                        paymentService.completeCancel(current, cancelled),
                        cancelled,
                    )
                }!!
            }

            // 비동기 승인 — 아직 PG도 깎지 않았다. 여기서 원장을 깎으면 대사가 금액 불일치 오보를 낸다
            PaymentCancelStatus.REQUESTED -> {
                log.info(
                    "PG가 취소를 접수만 함 — 원장 반영 보류. paymentId={}, requestedAmount={}",
                    payment.paymentId,
                    target.requestedAmount,
                )
                CancelPaymentResult.of(CancelPaymentStatus.REQUESTED, payment, cancelledAmount = 0L)
            }

            PaymentCancelStatus.FAILED -> {
                releaseCancelRequest(command.orderGroupId)
                throw BusinessException(PaymentErrorCode.PAYMENT_CANCEL_FAILED)
            }
        }
    }

    /** 선점 해제는 보상 동작이라 실패해도 원래 예외를 가리지 않는다 — 남은 CANCEL_REQUESTED는 대사가 본다 */
    private fun releaseCancelRequest(orderGroupId: Long) {
        runCatching {
            cancelTx.execute { paymentService.failCancel(paymentService.getByOrderGroupId(orderGroupId)) }
        }.onFailure { log.error("취소 선점 해제 실패 — 결제 행이 요청 상태로 남음. orderGroupId={}", orderGroupId, it) }
    }

    /** 콜백·웹훅·대사가 공유하는 확정 본체. 대상을 어떻게 찾았는지(로그인 대조냐 서명이냐 서버 내부냐)만 입구가 다르다 */
    private fun complete(
        target: PaymentService.CompletionTarget,
        buyerId: Long,
    ): CompletePaymentResult {
        // 1단계: 포트원에 물어보지 않아도 답이 정해지는 경우를 먼저 끝낸다
        completeWithoutGateway(target)?.let { return it }
        val payment = requireNotNull(target.payment) { "1단계를 지나왔으면 결제 행이 있어야 합니다 (orderGroupPublicId=${target.group.publicId})" }
        // 2단계: 결제가 실제로 어떻게 됐는지는 포트원만 안다 — 물어보고(트랜잭션 밖) 대답대로 처리한다
        val view = paymentGateway.find(payment.paymentId)
        return completeByGatewayStatus(buyerId, target.group, payment, view)
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
        buyerId: Long,
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
                completeAsPaid(buyerId, group, payment, view)
            }

            PaymentGatewayStatus.FAILED -> {
                tx.execute {
                    // 밖에서 로딩한 결제 행은 낡았을 수 있어 저장 직전에 다시 읽는다 — 그새 승인됐다면 fail()이 막는다
                    val current = paymentService.getByOrderGroupId(group.id)
                    CompletePaymentResult.of(CompletePaymentStatus.FAILED, group, paymentService.markFailed(current, view))
                }!!
            }

            else -> {
                throw BusinessException(PaymentErrorCode.PAYMENT_STATE_NOT_SUPPORTED)
            }
        }

    /** "승인됐다"는 대답의 마무리 — 금액 대조를 통과하면 한 트랜잭션에서 주문·결제를 결제완료로 만들고, 확정을 성사시킨 쪽만 커밋 뒤 장바구니를 정리한다 */
    private fun completeAsPaid(
        buyerId: Long,
        group: OrderGroup,
        payment: Payment,
        view: PaymentGatewayView,
    ): CompletePaymentResult {
        paymentService.assertAmountsMatch(group, payment, view)
        val (confirmation, result) =
            tx.execute {
                when (val confirmation = orderUseCase.confirmGroupPaid(group.id)) {
                    ConfirmGroupPaidResult.CONFIRMED -> {
                        // 밖에서 로딩한 결제 행은 낡았을 수 있어 저장 직전에 다시 읽는다
                        val current = paymentService.getByOrderGroupId(group.id)
                        confirmation to
                            CompletePaymentResult.of(CompletePaymentStatus.PAID, group, paymentService.approve(current, view))
                    }

                    ConfirmGroupPaidResult.ALREADY_PAID -> {
                        // 다른 입구가 승인 기록까지 마치고 커밋한 뒤에만 선점에 지므로, 기록을 다시 만들지 않는다
                        confirmation to
                            CompletePaymentResult.of(CompletePaymentStatus.PAID, group, paymentService.getByOrderGroupId(group.id))
                    }

                    ConfirmGroupPaidResult.ALREADY_CANCELLED -> {
                        // 돈은 나갔는데 주문은 취소된 사건 — 환불 배선 전까지는 이 로그가 유일한 흔적이다
                        log.error(
                            "승인된 결제의 주문그룹이 취소됨 — 환불 필요. orderGroupPublicId={}, paymentId={}, pgTxId={}, paidAmount={}",
                            group.publicId,
                            payment.paymentId,
                            view.pgTxId,
                            view.paidAmount,
                        )
                        throw BusinessException(PaymentErrorCode.PAYMENT_ORDER_GROUP_CANCELLED)
                    }
                }
            }!!
        // 패자까지 정리하면 확정 직후 같은 조합·수량으로 다시 담은 항목을 지울 수 있다 — 정리는 승자의 후처리
        if (confirmation == ConfirmGroupPaidResult.CONFIRMED) clearCart(buyerId, group)
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
