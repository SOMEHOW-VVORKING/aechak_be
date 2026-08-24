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

    // 리뷰 신고
    INVALID_REVIEW_REPORT_STATUS_TRANSITION(110100, "허용되지 않는 리뷰 신고 처리 상태 전이입니다.", 400),
}
