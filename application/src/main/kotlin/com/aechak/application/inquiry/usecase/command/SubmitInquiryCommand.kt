package com.aechak.application.inquiry.usecase.command

import com.aechak.domain.inquiry.inquiry.Inquiry
import com.aechak.domain.inquiry.inquiry.enums.InquiryType

data class SubmitInquiryCommand(
    val userId: Long,
    val inquiryType: InquiryType,
    val replyEmail: String,
    val content: String,
) {
    fun toEntity(): Inquiry = Inquiry.receive(userId, inquiryType, replyEmail, content)
}
