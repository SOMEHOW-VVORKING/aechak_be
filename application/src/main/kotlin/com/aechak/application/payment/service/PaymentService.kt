package com.aechak.application.payment.service

import com.aechak.application.payment.port.PaymentGatewayView
import com.aechak.application.payment.usecase.command.CompletePaymentCommand
import com.aechak.application.payment.usecase.command.PreparePaymentCommand
import com.aechak.common.error.BusinessException
import com.aechak.domain.order.group.OrderGroup
import com.aechak.domain.order.group.enums.OrderGroupStatus
import com.aechak.domain.order.group.repository.OrderGroupRepository
import com.aechak.domain.payment.Payment
import com.aechak.domain.payment.error.PaymentErrorCode
import com.aechak.domain.payment.repository.PaymentRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class PaymentService(
    private val paymentRepository: PaymentRepository,
    private val orderGroupRepository: OrderGroupRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** 이미 있는 행이면 그대로 돌려줌. 없으면 만들어 저장 */
    fun prepare(command: PreparePaymentCommand): Payment {
        val group =
            orderGroupRepository.findByPublicId(command.orderGroupPublicId)
                ?: throw BusinessException(PaymentErrorCode.PAYMENT_ORDER_GROUP_NOT_FOUND)

        validPrepare(group, command)

        paymentRepository.findByOrderGroupId(group.id)?.let { return it }
        return paymentRepository.save(
            Payment.prepare(
                orderGroupId = group.id,
                paymentId = group.publicId,
                method = command.method,
                targetAmount = group.finalPaymentAmount,
            ),
        )
    }

    private fun validPrepare(
        group: OrderGroup,
        command: PreparePaymentCommand,
    ) {
        if (group.buyerId != command.buyerId) {
            log.warn("타인의 주문그룹 결제 예약 시도. orderGroupPublicId={}, buyerId={}", command.orderGroupPublicId, command.buyerId)
            throw BusinessException(PaymentErrorCode.PAYMENT_ORDER_GROUP_NOT_FOUND)
        }

        if (group.status == OrderGroupStatus.PAID) {
            throw BusinessException(PaymentErrorCode.PAYMENT_ALREADY_PAID)
        }
        if (group.status != OrderGroupStatus.PENDING_PAYMENT) {
            throw BusinessException(PaymentErrorCode.PAYMENT_ORDER_GROUP_NOT_PAYABLE)
        }

        if (group.isExpired(LocalDateTime.now())) {
            throw BusinessException(PaymentErrorCode.PAYMENT_ORDER_GROUP_NOT_PAYABLE, detail = "결제 가능 시간이 만료되었습니다.")
        }
        if (group.finalPaymentAmount <= 0) {
            throw BusinessException(PaymentErrorCode.INVALID_PAYMENT_AMOUNT, detail = "전액 적립금 결제는 아직 지원하지 않습니다.")
        }
    }

    /** 확정 대상 로딩 — 본인 소유만. 만료 검사는 하지 않는다: 승인이 났다면 뒤늦은 확정을 허용하는 게 스펙 */
    fun loadCompletionTarget(command: CompletePaymentCommand): CompletionTarget {
        val group =
            orderGroupRepository.findByPublicId(command.orderGroupPublicId)
                ?: throw BusinessException(PaymentErrorCode.PAYMENT_ORDER_GROUP_NOT_FOUND)
        if (group.buyerId != command.buyerId) {
            log.warn("타인의 주문그룹 결제 확정 시도. orderGroupPublicId={}, buyerId={}", command.orderGroupPublicId, command.buyerId)
            throw BusinessException(PaymentErrorCode.PAYMENT_ORDER_GROUP_NOT_FOUND)
        }
        return CompletionTarget(group, paymentRepository.findByOrderGroupId(group.id))
    }

    /** 대사용 — 결제 행과 그 그룹을 함께 읽는다. 결제가 없으면 null, 그룹이 없으면(깨진 데이터) group만 null */
    fun findLedgerByPaymentId(paymentId: String): Ledger? {
        val payment = paymentRepository.findByPaymentId(paymentId) ?: return null
        return Ledger(payment, orderGroupRepository.findById(payment.orderGroupId))
    }

    /** 대사용 — 창 안에서 상태가 바뀐 결제 식별자 */
    fun findPaymentIdsUpdatedBetween(
        from: LocalDateTime,
        until: LocalDateTime,
    ): List<String> = paymentRepository.findPaymentIdsUpdatedBetween(from, until)

    /**
     * 웹훅 경로의 확정 대상 로딩 — 결제 행이 진입점이고 구매자는 그룹에서 읽는다.
     * 소유권 검사가 없는 이유는 대조할 로그인 주체가 없기 때문이고, 대신 서명이 발신자를 보증한다.
     * 우리가 모르는 결제(다른 상점·테스트 발신)면 null.
     */
    fun findCompletionTargetByPaymentId(paymentId: String): CompletionTarget? {
        val payment = paymentRepository.findByPaymentId(paymentId) ?: return null
        val group = orderGroupRepository.findById(payment.orderGroupId)
        if (group == null) {
            // 결제 행은 항상 그룹을 가리키므로 여기 오면 데이터가 깨진 것 — 웹훅을 다시 받아도 못 고친다
            log.error("결제 행이 가리키는 주문그룹이 없음. paymentId={}, orderGroupId={}", paymentId, payment.orderGroupId)
            return null
        }
        return CompletionTarget(group, payment)
    }

    /**
     * 3중 대조 — 주문금액·등록금액·실결제액이 전부 같아야 한다.
     * 등록금액과 실결제액만 대조하면 사전등록에 잘못 실린 금액이 그대로 통과한다.
     * 불일치는 확정도 실패 처리도 하지 않는다 — 돈은 받았는데 금액이 이상한 사건이라 사람이 확인해야 한다.
     */
    fun assertAmountsMatch(
        group: OrderGroup,
        payment: Payment,
        view: PaymentGatewayView,
    ) {
        if (group.finalPaymentAmount != payment.targetAmount || payment.targetAmount != view.paidAmount) {
            log.error(
                "결제 금액 3중 대조 실패 — 사람 확인 필요. orderGroupPublicId={}, 주문금액={}, 등록금액={}, 실결제액={}",
                group.publicId,
                group.finalPaymentAmount,
                payment.targetAmount,
                view.paidAmount,
            )
            throw BusinessException(PaymentErrorCode.PAYMENT_AMOUNT_MISMATCH)
        }
    }

    fun approve(
        payment: Payment,
        view: PaymentGatewayView,
    ): Payment = paymentRepository.save(payment.approve(pgTxId = view.pgTxId, realPaidAmount = view.paidAmount))

    fun markFailed(
        payment: Payment,
        view: PaymentGatewayView,
    ): Payment = paymentRepository.save(payment.fail(view.failureCode))

    fun getByOrderGroupId(orderGroupId: Long): Payment =
        paymentRepository.findByOrderGroupId(orderGroupId)
            ?: throw BusinessException(PaymentErrorCode.PAYMENT_NOT_FOUND)

    /**
     * 취소 선점. 금액을 생략하면 남은 취소가능액 전액이고, 그 경우 PG에는 금액을 싣지 않는다
     * (포트원은 금액 없는 요청을 전액 취소로 처리한다).
     */
    fun requestCancel(
        orderGroupId: Long,
        amount: Long?,
    ): CancelTarget {
        val payment = getByOrderGroupId(orderGroupId)
        val cancellable = payment.cancellableAmount ?: throw BusinessException(PaymentErrorCode.PAYMENT_NOT_CANCELLABLE)
        val requested = amount ?: cancellable
        return CancelTarget(
            payment = paymentRepository.save(payment.requestCancel(requested)),
            requestedAmount = requested,
            cancellableBefore = cancellable,
        )
    }

    /** PG가 확정한 금액만 취소가능액에서 뺀다 — 원승인액(등록금액·실결제액)은 그대로 둔다 */
    fun completeCancel(
        payment: Payment,
        cancelledAmount: Long,
    ): Payment = paymentRepository.save(payment.completeCancel(cancelledAmount))

    /** 취소 선점 해제 — 누적 취소액이 있으면 부분취소로, 없으면 승인으로 되돌린다 */
    fun failCancel(payment: Payment): Payment = paymentRepository.save(payment.failCancel())

    data class CompletionTarget(
        val group: OrderGroup,
        val payment: Payment?,
    )

    /** cancellableBefore 는 PG에 보낼 취소가능액 대조값 — 포트원 취소 API에 멱등키가 없어 중복 취소 방어가 여기 걸려 있다 */
    data class CancelTarget(
        val payment: Payment,
        val requestedAmount: Long,
        val cancellableBefore: Long,
    ) {
        /** 남은 전액을 취소하는 요청인가 — 그러면 PG에 금액을 싣지 않는다 */
        val isFullCancel: Boolean get() = requestedAmount == cancellableBefore
    }

    data class Ledger(
        val payment: Payment,
        val group: OrderGroup?,
    )
}
