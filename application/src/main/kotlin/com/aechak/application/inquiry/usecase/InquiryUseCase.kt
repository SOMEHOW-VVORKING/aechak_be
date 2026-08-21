package com.aechak.application.inquiry.usecase

import com.aechak.application.inquiry.usecase.command.SubmitInquiryCommand
import com.aechak.application.inquiry.usecase.result.SubmitInquiryResult

interface InquiryUseCase {
    fun submitInquiry(command: SubmitInquiryCommand): SubmitInquiryResult
}
