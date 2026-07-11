package com.aechak.domain.review.error

import com.aechak.common.error.ErrorCode

enum class ReviewErrorCode(
    override val code: Int,
    override val message: String,
    override val status: Int,
) : ErrorCode {

    REVIEW_NOT_FOUND(40501, "리뷰를 찾을 수 없습니다.", 404),
    INVALID_REVIEW_RATING(40502, "별점은 1~5 사이여야 합니다.", 400),
    INVALID_REVIEW_STATUS_TRANSITION(40503, "허용되지 않는 리뷰 상태 전이입니다.", 400),
    INVALID_REVIEW_REPORT_STATUS_TRANSITION(40504, "허용되지 않는 리뷰 신고 처리 상태 전이입니다.", 400),
}
