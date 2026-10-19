package com.aechak.domain.review.error

import com.aechak.common.error.ErrorCode

enum class ReviewErrorCode(
    override val code: Int,
    override val message: String,
    override val status: Int,
) : ErrorCode {
    // 리뷰
    REVIEW_NOT_FOUND(110000, "리뷰를 찾을 수 없습니다.", 404),
    INVALID_REVIEW_RATING(110001, "별점은 1~5 사이여야 합니다.", 400),
    INVALID_REVIEW_STATUS_TRANSITION(110002, "허용되지 않는 리뷰 상태 전이입니다.", 400),
    REVIEW_ACCESS_DENIED(110003, "본인 리뷰만 삭제할 수 있습니다.", 403),
    REVIEW_ALREADY_WRITTEN(110004, "이미 리뷰를 작성한 주문입니다.", 409),
    REVIEW_ORDER_ITEM_NOT_FOUND(110005, "리뷰를 작성할 수 있는 주문 품목을 찾을 수 없습니다.", 404),
    REVIEW_NOT_PURCHASE_CONFIRMED(110007, "구매확정한 주문만 리뷰를 작성할 수 있습니다.", 400),
    REVIEW_WINDOW_EXPIRED(110008, "구매확정 30일 이내에만 리뷰를 작성할 수 있습니다.", 400),
    REVIEW_TOO_MANY_IMAGES(110009, "리뷰 사진은 최대 5장까지 첨부할 수 있습니다.", 400),
    REVIEW_ITEM_NOT_REVIEWABLE(110010, "취소·반품한 주문 품목은 리뷰를 작성할 수 없습니다.", 400),

    // 리뷰 신고
    INVALID_REVIEW_REPORT_STATUS_TRANSITION(110100, "허용되지 않는 리뷰 신고 처리 상태 전이입니다.", 400),
}
