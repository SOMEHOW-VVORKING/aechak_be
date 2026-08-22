package com.aechak.application.payment.service

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
}
