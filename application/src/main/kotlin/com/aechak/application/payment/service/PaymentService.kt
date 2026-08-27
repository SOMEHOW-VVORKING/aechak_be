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

    fun validPrepare(
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

    data class CompletionTarget(
        val group: OrderGroup,
        val payment: Payment?,
    )
}
