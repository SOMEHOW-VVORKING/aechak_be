package com.aechak.domain.inquiry.product.enums

/**
 * 상품 문의 상태. 접수에서만 갈라진다 — 답변되면 철회할 수 없고, 철회되면 답변할 수 없다.
 */
enum class ProductInquiryStatus {
    RECEIVED, // 접수 — 답변 대기
    ANSWERED, // 답변 완료
    WITHDRAWN, // 구매자가 철회
    ;

    fun isClosed(): Boolean = this != RECEIVED
}
