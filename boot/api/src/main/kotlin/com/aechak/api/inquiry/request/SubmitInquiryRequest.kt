package com.aechak.api.inquiry.request

import com.aechak.application.inquiry.usecase.command.SubmitInquiryCommand
import com.aechak.domain.inquiry.inquiry.Inquiry
import com.aechak.domain.inquiry.inquiry.enums.InquiryType
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

/**
 * 문의 접수 요청
 */
data class SubmitInquiryRequest(
    @field:NotNull(message = "문의 유형은 필수입니다.")
    var inquiryType: InquiryType?,
    @field:NotBlank(message = "답변받을 이메일은 필수입니다.")
    @field:Email(message = "이메일 형식이 올바르지 않습니다.")
    @field:Size(max = 255, message = "이메일은 {max}자를 넘을 수 없습니다.")
    val replyEmail: String,
    @field:NotBlank(message = "문의 내용은 필수입니다.")
    @field:Size(max = Inquiry.MAX_CONTENT_LENGTH, message = "문의 내용은 {max}자를 넘을 수 없습니다.")
    val content: String,
) {
    fun toCommand(userId: Long): SubmitInquiryCommand =
        SubmitInquiryCommand(
            userId = userId,
            inquiryType = inquiryType!!,
            replyEmail = replyEmail,
            content = content,
        )
}
