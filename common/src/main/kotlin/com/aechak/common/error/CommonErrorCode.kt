package com.aechak.common.error

/**
 * 서버 공통(90000번대) 에러 코드.
 * 특정 도메인에 속하지 않아 common에 위치하는 유일한 enum.
 */
enum class CommonErrorCode(
    override val code: Int,
    override val message: String,
    override val status: Int,
) : ErrorCode {
    INTERNAL_SERVER_ERROR(90000, "서버 오류가 발생했습니다.", 500),
    INVALID_REQUEST(90001, "잘못된 요청입니다.", 400),
    INVALID_CURSOR(90002, "유효하지 않은 커서입니다.", 400),
    CONCURRENT_MODIFICATION(90003, "다른 곳에서 먼저 수정되었습니다. 새로고침 후 다시 시도해 주세요.", 409),

    // 멱등키는 도메인이 아니라 API 공통 메커니즘 — 소비 EP가 늘어도 코드는 하나여야 한다
    INVALID_IDEMPOTENCY_KEY(90004, "멱등키가 올바르지 않습니다.", 400),
    IDEMPOTENCY_KEY_ACCESS_DENIED(90005, "본인의 요청만 재시도할 수 있습니다.", 403),
}
