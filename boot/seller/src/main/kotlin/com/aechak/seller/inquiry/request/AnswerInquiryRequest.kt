package com.aechak.seller.inquiry.request

import com.aechak.application.inquiry.product.usecase.command.AnswerProductInquiryCommand
import com.aechak.domain.inquiry.product.ProductInquiry
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class AnswerInquiryRequest(
    @field:NotBlank(message = "답변 내용은 필수입니다.")
    @field:Size(max = ProductInquiry.ANSWER_CONTENT_MAX, message = "답변 내용은 {max}자를 넘을 수 없습니다.")
    val content: String,
) {
    fun toCommand(
        sellerId: Long,
        inquiryPublicId: String,
    ): AnswerProductInquiryCommand = AnswerProductInquiryCommand(sellerId = sellerId, inquiryPublicId = inquiryPublicId, content = content)
}
