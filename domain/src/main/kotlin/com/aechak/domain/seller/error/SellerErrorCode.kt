package com.aechak.domain.seller.error

import com.aechak.common.error.ErrorCode

enum class SellerErrorCode(
    override val code: Int,
    override val message: String,
    override val status: Int,
) : ErrorCode {
    SELLER_NOT_FOUND(10001, "셀러를 찾을 수 없습니다.", 404),
    SELLER_APPLICATION_NOT_FOUND(10003, "입점 신청을 찾을 수 없습니다.", 404),
    APPLICATION_STATUS_TRANSITION_NOT_ALLOWED(10004, "현재 신청 상태에서 허용되지 않는 전이입니다.", 400),
    REJECTION_REASON_REQUIRED(10005, "반려 사유는 필수입니다.", 400),
}
