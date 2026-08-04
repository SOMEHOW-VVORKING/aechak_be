package com.aechak.application.auth.error

import com.aechak.common.error.ErrorCode

/**
 * 인증/인가 = 20000번대 (에러코드 대역 합의 2026-07-13).
 * 코드·메시지는 API 계약의 에러코드표와 1:1 — 바꾸면 계약도 함께 바꾼다.
 * domain이 아닌 application 소속인 이유: auth는 세션 수명·소셜 신원 검증이라는 유스케이스 관심사로,
 * 별도 도메인 애그리거트를 갖지 않는다.
 */
enum class AuthErrorCode(
    override val code: Int,
    override val message: String,
    override val status: Int,
) : ErrorCode {
    INVALID_SOCIAL_TOKEN(20000, "소셜 로그인에 실패했습니다. 다시 시도해 주세요.", 401),
    UNSUPPORTED_PROVIDER(20001, "지원하지 않는 로그인 방식입니다.", 400),
    INVALID_REFRESH_TOKEN(20002, "유효하지 않은 토큰입니다.", 401),
    REFRESH_TOKEN_REUSED(20003, "보안을 위해 다시 로그인해 주세요.", 401),
    UNAUTHENTICATED(20004, "로그인이 필요합니다.", 401),
    ACCOUNT_BLOCKED(20005, "이용이 제한된 계정입니다.", 403),
    ONBOARDING_REQUIRED(20006, "온보딩을 완료해야 이용할 수 있습니다.", 403),
    APPLE_RELOGIN_REQUIRED(20007, "탈퇴를 위해 애플 로그인이 한 번 더 필요합니다.", 409),
    DISALLOWED_RETURN_URL(20008, "허용되지 않은 returnUrl입니다.", 400),
    INVALID_LOGIN_STATE(20009, "로그인 요청이 만료되었거나 유효하지 않습니다. 다시 시도해 주세요.", 400),

    /** HTTP 응답이 아니라 콜백 302의 ?error= 값으로만 나간다 — status는 명목값. */
    AUTHORIZATION_CODE_MISSING(20010, "소셜 로그인이 완료되지 않았습니다.", 401),
}
