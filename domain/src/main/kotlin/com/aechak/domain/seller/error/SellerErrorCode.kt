package com.aechak.domain.seller.error

import com.aechak.common.error.ErrorCode

enum class SellerErrorCode(
    override val code: Int,
    override val message: String,
    override val status: Int,
) : ErrorCode {
    // 셀러
    // 식별자로 지목한 셀러가 없을 때의 404. 요청자가 셀러가 아닌 경우는 아래 NOT_SELLER(403)다.
    SELLER_NOT_FOUND(10000, "셀러를 찾을 수 없습니다.", 404),
    ALREADY_SELLER(10001, "이미 셀러로 등록된 계정입니다.", 409),
    SELLER_NOT_ACTIVE(10002, "활성 상태의 셀러만 이용할 수 있습니다.", 403),
    SELLER_CENTER_READ_FORBIDDEN(10003, "셀러센터를 조회할 권한이 없습니다.", 403),

    // 셀러로 등록된 적이 없는 계정 — 정지·탈퇴(10002 등)와 다른 상태라 코드를 따로 쓴다.
    NOT_SELLER(10004, "셀러로 등록되지 않은 계정입니다. 입점 신청 후 이용할 수 있습니다.", 403),
    INVALID_SHIPPING_POLICY(10005, "배송 정책 값이 올바르지 않습니다.", 400),
    INVALID_STORE_SETTINGS(10006, "스토어 설정 값이 올바르지 않습니다.", 400),

    // 판매정지(어드민 셀러 계정 제재) — 계정 로그인 차단(UserStatus)과 다른 층이다
    SELLER_ALREADY_SUSPENDED(10007, "이미 판매정지된 셀러입니다.", 409),
    SELLER_NOT_SUSPENDED(10008, "판매정지 상태가 아닌 셀러입니다.", 409),

    // 탈퇴·탈퇴 요청 셀러의 정지, 그리고 동시 조치의 낙관적 락 충돌을 묶는 상태 충돌 의미
    SELLER_STATUS_TRANSITION_NOT_ALLOWED(10009, "현재 셀러 상태에서 허용되지 않는 전이입니다.", 409),
    SELLER_SANCTION_REASON_DETAIL_REQUIRED(10010, "기타 사유를 고르면 상세 사유가 필수입니다.", 400),
    SELLER_SANCTION_TEXT_TOO_LONG(10011, "제재 사유·메모가 허용 길이를 넘었습니다.", 400),

    // 입점 신청
    SELLER_APPLICATION_NOT_FOUND(10100, "입점 신청을 찾을 수 없습니다.", 404),

    // 400이었으나 계약 게이트에서 409 채택(SUBMITTED 잠금·이미 처리·동시 충돌을 묶는 상태 충돌 의미)
    APPLICATION_STATUS_TRANSITION_NOT_ALLOWED(10101, "현재 신청 상태에서 허용되지 않는 전이입니다.", 409),
    REJECTION_REASON_REQUIRED(10102, "반려 사유는 필수입니다.", 400),
    PHONE_VERIFICATION_REQUIRED(10103, "휴대폰 본인 인증이 필요합니다.", 403),
    APPLICATION_ALREADY_IN_PROGRESS(10104, "진행 중인 입점 신청이 있습니다.", 409),
    REQUIRED_DOCUMENTS_MISSING(10105, "유형별 필수 정보·서류가 미비합니다.", 400),
}
