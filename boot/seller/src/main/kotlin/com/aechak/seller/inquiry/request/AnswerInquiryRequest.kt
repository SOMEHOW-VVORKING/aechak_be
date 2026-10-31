package com.aechak.seller.inquiry.request

import com.aechak.application.inquiry.product.usecase.command.AnswerProductInquiryCommand

/**
 * 답변 등록 요청.
 *
 * 공백 여부와 길이는 **여기서 검증하지 않는다.** ProductInquiry가 소유한 규칙이고 전용
 * 에러코드(120105·120106)를 던지므로 DTO에 거울을 두면 그 코드 대신 90001이 나간다
 * (30-boot 문서 §3-1 — 도메인이 소유한 규칙은 DTO에 거울을 두지 않는다).
 */
data class AnswerInquiryRequest(
    val content: String,
) {
    fun toCommand(
        sellerId: Long,
        inquiryPublicId: String,
    ): AnswerProductInquiryCommand = AnswerProductInquiryCommand(sellerId = sellerId, inquiryPublicId = inquiryPublicId, content = content)
}
