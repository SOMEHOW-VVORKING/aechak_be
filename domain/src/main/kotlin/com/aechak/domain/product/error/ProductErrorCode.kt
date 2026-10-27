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
    TOO_MANY_PRODUCT_IMAGES(40003, "상품 이미지 수가 허용 범위를 넘었습니다.", 400),
    PRODUCT_SELLER_NOT_ACTIVE(40004, "활성 상태의 셀러만 상품을 등록할 수 있습니다.", 403),
    INVALID_PRODUCT_OPTIONS(40005, "옵션 구성이 올바르지 않습니다.", 400),
    PRODUCT_SELLER_READ_FORBIDDEN(40006, "탈퇴하거나 정지된 셀러는 상품을 조회할 수 없습니다.", 403),
    PRODUCT_ACCESS_DENIED(40007, "본인의 상품만 조회할 수 있습니다.", 403),

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

    // 임시저장
    PRODUCT_DRAFT_NOT_FOUND(40400, "임시저장한 상품을 찾을 수 없습니다.", 404),
    PRODUCT_DRAFT_LIMIT_EXCEEDED(40401, "임시저장은 최대 10개까지 보관할 수 있습니다.", 422),

    // 검수 큐
    PRODUCT_INSPECTION_NOT_PENDING(40500, "관리자 확인을 기다리는 상품 검수가 없습니다.", 409),

    // 셀러 이의 제기
    INSPECTION_APPEAL_REASON_REQUIRED(40501, "이의 제기 사유를 입력해 주세요.", 400),
    INSPECTION_APPEAL_NOT_ALLOWED(40502, "이의를 제기할 수 있는 상태가 아닙니다.", 409),
    INSPECTION_APPEAL_ALREADY_SUBMITTED(40503, "이미 이의를 제기했습니다. 상품을 고치면 다시 제기할 수 있습니다.", 409),
    INSPECTION_APPEAL_LIMIT_EXCEEDED(40504, "동시에 제기할 수 있는 이의 건수를 넘었습니다.", 429),
    INSPECTION_APPEAL_COOLDOWN(40505, "기각된 이의는 일정 시간이 지난 뒤 다시 제기할 수 있습니다.", 429),

    INSPECTION_APPEAL_ATTEMPTS_EXHAUSTED(40507, "이 상품에 제기할 수 있는 이의 횟수를 모두 사용했습니다.", 429),

    // 검수 운영 지표
    INSPECTION_METRICS_WINDOW_INVALID(40506, "지표 집계 창이 올바르지 않습니다.", 400),
}
