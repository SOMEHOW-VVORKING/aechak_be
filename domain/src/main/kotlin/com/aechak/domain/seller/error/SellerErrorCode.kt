package com.aechak.domain.seller.error

import com.aechak.common.error.ErrorCode

enum class SellerErrorCode(
    override val code: Int,
    override val message: String,
    override val status: Int,
) : ErrorCode {
    // 셀러
    SELLER_NOT_FOUND(10000, "셀러를 찾을 수 없습니다.", 404),
    ALREADY_SELLER(10001, "이미 셀러로 등록된 계정입니다.", 409),

    // 입점 신청
    SELLER_APPLICATION_NOT_FOUND(10100, "입점 신청을 찾을 수 없습니다.", 404),

    // 400이었으나 계약 게이트에서 409 채택(SUBMITTED 잠금·이미 처리·동시 충돌을 묶는 상태 충돌 의미)
    APPLICATION_STATUS_TRANSITION_NOT_ALLOWED(10101, "현재 신청 상태에서 허용되지 않는 전이입니다.", 409),
    REJECTION_REASON_REQUIRED(10102, "반려 사유는 필수입니다.", 400),
    PHONE_VERIFICATION_REQUIRED(10103, "휴대폰 본인 인증이 필요합니다.", 403),
    APPLICATION_ALREADY_IN_PROGRESS(10104, "진행 중인 입점 신청이 있습니다.", 409),
    REQUIRED_DOCUMENTS_MISSING(10105, "유형별 필수 정보·서류가 미비합니다.", 400),
}
