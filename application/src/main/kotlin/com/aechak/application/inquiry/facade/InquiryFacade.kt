package com.aechak.application.inquiry.facade

import com.aechak.application.inquiry.service.InquiryService
import com.aechak.application.inquiry.usecase.InquiryUseCase
import com.aechak.application.inquiry.usecase.command.SubmitInquiryCommand
import com.aechak.application.inquiry.usecase.result.SubmitInquiryResult
import com.aechak.domain.support.AggregateRoot
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

@Service
class InquiryFacade(
    private val inquiryService: InquiryService,
    private val eventPublisher: ApplicationEventPublisher,
    transactionManager: PlatformTransactionManager,
) : InquiryUseCase {
    private val log = LoggerFactory.getLogger(javaClass)
    private val tx = TransactionTemplate(transactionManager)

    override fun submitInquiry(command: SubmitInquiryCommand): SubmitInquiryResult {
        val inquiry =
            requireNotNull(
                tx.execute {
                    inquiryService.receive(command).also { received ->
                        received.registerReceived()
                        publishEvents(received)
                    }
                },
            )
        log.info("문의 접수 (inquiryId={}, inquiryType={})", inquiry.id, inquiry.inquiryType)
        return SubmitInquiryResult.from(inquiry)
    }

    private fun publishEvents(aggregate: AggregateRoot) {
        aggregate.events.forEach { event -> eventPublisher.publishEvent(event) }
        aggregate.clearEvents()
    }
}
