package com.aechak.domain.user.error

import com.aechak.common.error.ErrorCode

enum class UserErrorCode(
    override val code: Int,
    override val message: String,
    override val status: Int,
) : ErrorCode {
    USER_NOT_FOUND(20001, "사용자를 찾을 수 없습니다.", 404),
    DUPLICATE_NICKNAME(20002, "이미 사용 중인 닉네임입니다.", 409),
    INVALID_NICKNAME(20003, "닉네임 형식이 올바르지 않습니다.", 400),
    ALREADY_WITHDRAWN(20004, "이미 탈퇴한 계정입니다.", 409),
    INVALID_POINT_AMOUNT(20005, "적립금 거래 금액은 0보다 커야 합니다.", 400),
    INVALID_PET_WEIGHT(20006, "체중은 0보다 커야 합니다.", 400),
    SELF_REPORT_NOT_ALLOWED(20007, "자기 자신은 신고할 수 없습니다.", 400),
    REPORT_REASON_REQUIRED(20008, "기타 사유 신고는 상세 사유가 필요합니다.", 400),
}
