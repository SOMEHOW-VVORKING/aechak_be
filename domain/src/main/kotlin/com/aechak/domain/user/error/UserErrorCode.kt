package com.aechak.domain.user.error

import com.aechak.common.error.ErrorCode

/** 사용자 도메인 오류에는 30000번대 코드를 사용, 인증과 인가는 20000번대를 사용 */
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

    // 전화 인증 코드는 별도 도메인 모델이 아니므로 사용자 오류 코드에 이어서 배정한다.
    // 코드 오류와 번호 불일치는 SMS_CODE_INVALID로 통합해 인증번호의 정답 여부가 노출되지 않게 한다.
    // 만료, 시도 횟수 초과, 발송 제한은 사용자가 취해야 할 조치가 달라 별도 코드로 구분한다.
    SMS_CODE_INVALID(30005, "인증번호가 올바르지 않습니다.", 400),
    SMS_RESEND_COOLDOWN(30006, "재발송은 잠시 후에 가능합니다.", 429),
    SMS_SEND_FAILED(30007, "인증번호 발송에 실패했습니다. 잠시 후 다시 시도해 주세요.", 502),
    SMS_DAILY_LIMIT_EXCEEDED(30008, "오늘 발송 가능 횟수를 모두 사용했습니다. 내일 다시 시도해 주세요.", 429),
    SMS_CODE_EXPIRED(30009, "인증번호가 만료되었습니다. 재발송 시도해 주세요.", 400),
    SMS_ATTEMPTS_EXCEEDED(30010, "인증번호 입력 횟수를 초과했습니다. 재발송 시도해 주세요.", 429),

    // 탈퇴
    WITHDRAWAL_BLOCKED(30011, "정리되지 않은 거래가 있어 탈퇴할 수 없습니다.", 409),

    // 관리자 역할
    USER_ALREADY_ADMIN(30012, "이미 관리자인 계정입니다.", 409),
    LAST_ADMIN_CANNOT_BE_DEMOTED(30013, "마지막 관리자는 강등할 수 없습니다.", 409),
    SELF_ROLE_CHANGE_NOT_ALLOWED(30014, "자신의 역할은 변경할 수 없습니다.", 403),

    // 30015·30016은 다른 작업에 배정돼 있어 비워 둔다
    USER_NOT_PROMOTABLE(30017, "활성 상태인 계정만 관리자로 승격할 수 있습니다.", 409),

    // 회원 제재의 계정 상태 판정 — 검증 대상이 User라 User의 100번대를 쓴다
    PERMANENT_BAN_IS_FINAL(30018, "영구 정지된 계정은 상태를 바꿀 수 없습니다.", 409),
    NO_ACTIVE_SANCTION(30019, "해제할 제재가 없습니다.", 409),
    SELF_SANCTION_NOT_ALLOWED(30020, "자신을 제재할 수 없습니다.", 403),
    ADMIN_CANNOT_BE_SANCTIONED(30021, "관리자 계정은 제재할 수 없습니다. 먼저 강등해 주세요.", 409),
    SANCTION_PERIOD_NOT_IN_FUTURE(30022, "제재 기간은 현재보다 미래여야 합니다.", 400),
    SANCTION_REQUIRES_ONBOARDED_ACCOUNT(
        30023,
        "가입을 마치지 않은 계정에는 이용 제한·임시 정지를 걸 수 없습니다. 영구 정지나 탈퇴 처리를 사용해 주세요.",
        409,
    ),

    // 적립금
    INVALID_POINT_AMOUNT(30100, "적립금 거래 금액은 0보다 커야 합니다.", 400),
    INSUFFICIENT_POINT_BALANCE(30101, "적립금 잔액이 부족합니다.", 409),

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

    // 제재 이력 — 기록 자체의 입력 검증. 검증 대상이 UserSanction이라 별도 100번대를 받는다
    SANCTION_REASON_DETAIL_REQUIRED(30800, "기타 사유 제재는 상세 사유가 필요합니다.", 400),
    SANCTION_PERIOD_REQUIRED(30801, "임시 정지는 제재 기간이 필요합니다.", 400),
    SANCTION_PERIOD_NOT_ALLOWED(30802, "임시 정지가 아닌 제재에는 기간을 지정할 수 없습니다.", 400),
    SANCTION_TEXT_TOO_LONG(30803, "제재 사유 또는 메모가 너무 깁니다.", 400),
}
