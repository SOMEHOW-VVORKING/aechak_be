package com.aechak.application.payment.facade

import com.aechak.application.payment.port.PaymentGatewayPort
import com.aechak.application.payment.service.PaymentService
import com.aechak.application.payment.usecase.PaymentUseCase
import com.aechak.application.payment.usecase.command.PreparePaymentCommand
import com.aechak.application.payment.usecase.result.PreparePaymentResult
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

@Service
class PaymentFacade(
    private val paymentService: PaymentService,
    private val paymentGateway: PaymentGatewayPort,
    transactionManager: PlatformTransactionManager,
) : PaymentUseCase {
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
}
