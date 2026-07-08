package com.aechak.domain.user.error

import com.aechak.common.error.ErrorCode

/**
 * user 도메인 에러 코드 — 대역 20000~20999.
 * 새 코드는 이 파일에만 추가한다 (common/web-common은 건드리지 않는다).
 * status 관례: 대부분 400, NOT_FOUND 404, 중복 409, 인증 401/403, 외부 연동 실패 502.
 */
enum class UserErrorCode(
    override val code: Int,
    override val message: String,
    override val status: Int,
) : ErrorCode {

    USER_NOT_FOUND(20001, "사용자를 찾을 수 없습니다.", 404),
    DUPLICATE_NICKNAME(20002, "이미 사용 중인 닉네임입니다.", 409),
    // TODO: 도메인 기능 구현하며 추가
}
