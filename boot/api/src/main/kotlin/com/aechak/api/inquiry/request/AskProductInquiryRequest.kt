package com.aechak.api.inquiry.request

import com.aechak.application.inquiry.product.usecase.command.AskProductInquiryCommand
import com.aechak.domain.inquiry.product.enums.ProductInquiryCategory
import jakarta.validation.constraints.NotNull

/**
 * 상품 문의 등록 요청. 비공개로 두면 작성자와 해당 상품 셀러만 본문을 읽는다.
 *
 * 제목·본문의 공백 여부와 길이는 **여기서 검증하지 않는다.** 둘 다 ProductInquiry가 소유한
 * 개념의 규칙이고 전용 에러코드(120101~120104)를 던진다 — DTO에 거울을 두면 어노테이션이 먼저
 * 걸려 90001이 나가고 계약이 약속한 코드가 거짓이 된다(30-boot 문서 §3-1). 필드가 아예 없거나
 * 타입이 틀린 형식 오류는 그대로 본문 파싱 단계가 400으로 거절한다.
 */
data class AskProductInquiryRequest(
    @field:NotNull(message = "문의 유형은 필수입니다.")
    val category: ProductInquiryCategory?,
    val title: String,
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
