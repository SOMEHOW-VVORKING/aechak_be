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

    INTERNAL_SERVER_ERROR(90001, "서버 오류가 발생했습니다.", 500),
    INVALID_REQUEST(90002, "잘못된 요청입니다.", 400),
    // TODO: 90000번대 공통 코드 필요 시 여기에만 추가
}
