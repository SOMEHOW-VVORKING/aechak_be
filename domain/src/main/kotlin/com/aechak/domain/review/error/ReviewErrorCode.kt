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

    // 셀러 리뷰 관리
    REVIEW_SELLER_ACCESS_DENIED(110011, "탈퇴하거나 정지된 셀러는 리뷰를 조회·관리할 수 없습니다.", 403),

    // 운영자 블라인드 — 110012~110019는 셀러 리뷰 관리가 늘어날 자리로 비워 둔다
    REVIEW_ALREADY_BLINDED(110020, "이미 블라인드된 리뷰입니다.", 409),
    REVIEW_NOT_BLINDED(110021, "블라인드되지 않은 리뷰는 해제할 수 없습니다.", 409),
    INVALID_REVIEW_BLIND_REASON(110022, "처리 사유 메모는 500자 이하여야 합니다.", 400),

    // 리뷰 신고
    INVALID_REVIEW_REPORT_STATUS_TRANSITION(110100, "허용되지 않는 리뷰 신고 처리 상태 전이입니다.", 400),
    REVIEW_SELF_REPORT_NOT_ALLOWED(110101, "본인 리뷰는 신고할 수 없습니다.", 400),
    REVIEW_REPORT_REASON_TEXT_REQUIRED(110102, "기타 사유 신고는 상세 사유가 필요합니다.", 400),
    REVIEW_ALREADY_REPORTED(110103, "이미 신고한 리뷰입니다.", 409),

    // 셀러 답글
    REVIEW_REPLY_NOT_FOUND(110200, "답글을 찾을 수 없습니다.", 404),
    REVIEW_REPLY_ALREADY_EXISTS(110201, "이미 답글을 등록한 리뷰입니다.", 409),
    INVALID_REVIEW_REPLY_CONTENT(110202, "답글은 1~500자여야 합니다.", 400),
}
