package com.aechak.application.inquiry.service

import com.aechak.application.inquiry.usecase.command.SubmitInquiryCommand
import com.aechak.domain.inquiry.inquiry.Inquiry
import com.aechak.domain.inquiry.inquiry.repository.InquiryRepository
import org.springframework.stereotype.Service

@Service
class InquiryService(
    private val inquiryRepository: InquiryRepository,
) {
    fun receive(command: SubmitInquiryCommand): Inquiry = inquiryRepository.save(command.toEntity())
}
