package com.aechak.domain.user.error

import com.aechak.common.error.ErrorCode

/** 사용자 도메인 = 30000번대. 20000번대는 인증/인가 소속(에러코드 대역 합의 2026-07-13). */
enum class UserErrorCode(
    override val code: Int,
    override val message: String,
    override val status: Int,
) : ErrorCode {
    // 사용자
    USER_NOT_FOUND(30000, "사용자를 찾을 수 없습니다.", 404),
    DUPLICATE_NICKNAME(30001, "이미 사용 중인 닉네임입니다.", 409),
    INVALID_NICKNAME(30002, "닉네임 형식이 올바르지 않습니다.", 400),
    ALREADY_WITHDRAWN(30003, "이미 탈퇴한 계정입니다.", 409),
    ONBOARDING_ALREADY_COMPLETED(30004, "이미 온보딩을 완료한 계정입니다.", 409),

    // 전화 인증 — 인증 코드는 세션류(애그리거트 아님)라 User 루트 대역을 잇는다
    // 코드·번호 불일치만 한 코드로 묶는다(구분 비노출 — "코드는 맞고 번호가 틀렸다"가 새면 6자리 정답 여부가 드러난다).
    // 만료·시도 초과·상한류는 호출자가 이미 아는 사실이라 구분해도 새는 정보가 없고, 다음 행동이 서로 다르다.
    SMS_CODE_INVALID(30005, "인증번호가 올바르지 않습니다.", 400),
    SMS_RESEND_COOLDOWN(30006, "재발송은 잠시 후에 가능합니다.", 429),
    SMS_SEND_FAILED(30007, "인증번호 발송에 실패했습니다. 잠시 후 다시 시도해 주세요.", 502),
    SMS_DAILY_LIMIT_EXCEEDED(30008, "오늘 발송 가능 횟수를 모두 사용했습니다. 내일 다시 시도해 주세요.", 429),
    SMS_CODE_EXPIRED(30009, "인증번호가 만료되었습니다. 재발송 시도해 주세요.", 400),
    SMS_ATTEMPTS_EXCEEDED(30010, "인증번호 입력 횟수를 초과했습니다. 재발송 시도해 주세요.", 429),

    // 적립금
    INVALID_POINT_AMOUNT(30100, "적립금 거래 금액은 0보다 커야 합니다.", 400),

    // 신고
    SELF_REPORT_NOT_ALLOWED(30200, "자기 자신은 신고할 수 없습니다.", 400),
    REPORT_REASON_REQUIRED(30201, "기타 사유 신고는 상세 사유가 필요합니다.", 400),

    // 약관 동의
    REQUIRED_CONSENT_MISSING(30300, "필수 약관에 동의해 주세요.", 403),

    // 약관
    INVALID_TERM(30400, "유효하지 않은 약관입니다.", 400),

    // 배송지
    DELIVERY_ADDRESS_LIMIT_EXCEEDED(30500, "배송지는 최대 10개까지만 등록할 수 있습니다.", 422),
    DELIVERY_ADDRESS_NOT_FOUND(30501, "배송지를 찾을 수 없습니다.", 404),
    DELIVERY_ADDRESS_ACCESS_DENIED(30502, "본인의 배송지만 접근할 수 있습니다.", 403),

    // 펫
    PET_PROFILE_LIMIT_EXCEEDED(30600, "펫은 최대 10마리까지 등록할 수 있습니다.", 422),
    PET_PROFILE_NOT_FOUND(30601, "펫 프로필을 찾을 수 없습니다.", 404),
    PET_PROFILE_ACCESS_DENIED(30602, "본인의 펫만 접근할 수 있습니다.", 403),
    INVALID_PET_WEIGHT(30603, "체중은 0.1~200.0kg 범위여야 합니다.", 400),
    INVALID_PET_BIRTH_YEAR_MONTH(30604, "생년월은 이번 달보다 미래일 수 없습니다.", 400),

    // 품종
    // 미존재와 종 불일치를 한 코드로 묶음. 클라이언트가 할 일이 같음
    INVALID_BREED(30700, "선택한 품종이 올바르지 않습니다.", 400),
}
