package com.aechak.domain.product.error

import com.aechak.common.error.ErrorCode

enum class ProductErrorCode(
    override val code: Int,
    override val message: String,
    override val status: Int,
) : ErrorCode {
    // 상품
    PRODUCT_NOT_FOUND(40000, "상품을 찾을 수 없습니다.", 404),
    INVALID_PRODUCT_PRICE(40001, "상품 가격이 올바르지 않습니다.", 400),
    INVALID_DISCOUNT_PERIOD(40002, "할인 기간이 올바르지 않습니다.", 400),

    // 카테고리
    INVALID_CATEGORY_DEPTH(40100, "카테고리 단계와 부모 참조가 일치하지 않습니다.", 400),
    CATEGORY_NOT_FOUND(40101, "카테고리를 찾을 수 없습니다.", 404),
    INVALID_CATEGORY_FILTER(40102, "카테고리 필터는 중분류만 사용할 수 있습니다.", 400),

    // 옵션
    INVALID_OPTION_STOCK(40200, "옵션 재고는 음수일 수 없습니다.", 400),
    INVALID_OPTION_ADDITIONAL_PRICE(40201, "옵션 추가금은 음수일 수 없습니다.", 400),

    // 신고
    PRODUCT_REPORT_REASON_TEXT_REQUIRED(40300, "기타 사유 신고는 상세 사유가 필요합니다.", 400),
    INVALID_PRODUCT_REPORT_STATUS_TRANSITION(40301, "허용되지 않는 신고 처리 상태 전이입니다.", 400),
}
