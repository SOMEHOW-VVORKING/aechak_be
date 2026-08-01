package com.aechak.domain.user.error

import com.aechak.common.error.ErrorCode

/** 사용자 도메인 = 30000번대. 20000번대는 인증/인가 소속(에러코드 대역 합의 2026-07-13). */
enum class UserErrorCode(
    override val code: Int,
    override val message: String,
    override val status: Int,
) : ErrorCode {
    USER_NOT_FOUND(30001, "사용자를 찾을 수 없습니다.", 404),
    DUPLICATE_NICKNAME(30002, "이미 사용 중인 닉네임입니다.", 409),
    INVALID_NICKNAME(30003, "닉네임 형식이 올바르지 않습니다.", 400),
    ALREADY_WITHDRAWN(30004, "이미 탈퇴한 계정입니다.", 409),
    INVALID_POINT_AMOUNT(30005, "적립금 거래 금액은 0보다 커야 합니다.", 400),

    SELF_REPORT_NOT_ALLOWED(30007, "자기 자신은 신고할 수 없습니다.", 400),
    REPORT_REASON_REQUIRED(30008, "기타 사유 신고는 상세 사유가 필요합니다.", 400),
    REQUIRED_CONSENT_MISSING(30009, "필수 약관에 동의해 주세요.", 403),
    INVALID_TERM(30010, "유효하지 않은 약관입니다.", 400),

    ONBOARDING_ALREADY_COMPLETED(30013, "이미 온보딩을 완료한 계정입니다.", 409),

    // 배송지 서브대역(30120~) — 주문 대역이 아니라 여기인 이유: 배송지는 User 애그리거트 소속(BC 기준).
    DELIVERY_ADDRESS_LIMIT_EXCEEDED(30120, "배송지는 최대 10개까지만 등록할 수 있습니다.", 422),
    DELIVERY_ADDRESS_NOT_FOUND(30121, "배송지를 찾을 수 없습니다.", 404),
    DELIVERY_ADDRESS_ACCESS_DENIED(30122, "본인의 배송지만 접근할 수 있습니다.", 403),

    // 펫 서브대역(30130~). 펫은 User 애그리거트 소속
    PET_PROFILE_LIMIT_EXCEEDED(30130, "펫은 최대 10마리까지 등록할 수 있습니다.", 422),
    PET_PROFILE_NOT_FOUND(30131, "펫 프로필을 찾을 수 없습니다.", 404),
    PET_PROFILE_ACCESS_DENIED(30132, "본인의 펫만 접근할 수 있습니다.", 403),
    INVALID_PET_WEIGHT(30133, "체중은 0.1~200.0kg 범위여야 합니다.", 400),

    // 미존재와 종 불일치를 한 코드로 묶음. 클라이언트가 할 일이 같음
    INVALID_BREED(30134, "선택한 품종이 올바르지 않습니다.", 400),
    INVALID_PET_BIRTH_YEAR_MONTH(30135, "생년월은 이번 달보다 미래일 수 없습니다.", 400),
    PET_PROFILE_VERSION_CONFLICT(30136, "다른 곳에서 먼저 수정되었습니다. 새로고침 후 다시 시도해 주세요.", 409),
}
