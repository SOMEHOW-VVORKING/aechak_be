package com.aechak.api.inquiry.request

import com.aechak.application.inquiry.product.usecase.command.AskProductInquiryCommand
import com.aechak.domain.inquiry.product.ProductInquiry
import com.aechak.domain.inquiry.product.enums.ProductInquiryCategory
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

/** 상품 문의 등록 요청. 비공개로 두면 작성자와 해당 상품 셀러만 본문을 읽는다. */
data class AskProductInquiryRequest(
    @field:NotNull(message = "문의 유형은 필수입니다.")
    val category: ProductInquiryCategory?,
    @field:NotBlank(message = "문의 제목은 필수입니다.")
    @field:Size(max = ProductInquiry.TITLE_MAX, message = "문의 제목은 {max}자를 넘을 수 없습니다.")
    val title: String,
    @field:NotBlank(message = "문의 내용은 필수입니다.")
    @field:Size(max = ProductInquiry.CONTENT_MAX, message = "문의 내용은 {max}자를 넘을 수 없습니다.")
    val content: String,
    val secret: Boolean = false,
) {
    fun toCommand(
        authorUserId: Long,
        productPublicId: String,
    ): AskProductInquiryCommand =
        AskProductInquiryCommand(
            authorUserId = authorUserId,
            productPublicId = productPublicId,
            category = category!!,
            title = title,
            content = content,
            secret = secret,
        )
}
