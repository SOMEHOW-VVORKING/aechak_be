package com.aechak.domain.product.error

import com.aechak.common.error.ErrorCode

enum class ProductErrorCode(
    override val code: Int,
    override val message: String,
    override val status: Int,
) : ErrorCode {
    PRODUCT_NOT_FOUND(40001, "상품을 찾을 수 없습니다.", 404),
    INVALID_PRODUCT_PRICE(40002, "상품 가격이 올바르지 않습니다.", 400),

    INVALID_CATEGORY_DEPTH(40011, "카테고리 단계와 부모 참조가 일치하지 않습니다.", 400),

    INVALID_OPTION_STOCK(40021, "옵션 재고는 음수일 수 없습니다.", 400),
    INVALID_OPTION_ADDITIONAL_PRICE(40022, "옵션 추가금은 음수일 수 없습니다.", 400),

    PRODUCT_REPORT_REASON_TEXT_REQUIRED(40030, "기타 사유 신고는 상세 사유가 필요합니다.", 400),
    INVALID_PRODUCT_REPORT_STATUS_TRANSITION(40031, "허용되지 않는 신고 처리 상태 전이입니다.", 400),
}
