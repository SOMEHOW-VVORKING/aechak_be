package com.aechak.webcommon.error

import com.aechak.common.error.ErrorCode

/**
 * 실패 응답 본문 (HTTP wire format).
 *
 * 규칙:
 * - errorCode(int) + message만. timestamp 없음(추적은 X-Trace-Id 헤더).
 * - status는 본문에 넣지 않음 (HTTP Status로 전달).
 * - 클라이언트는 message가 아닌 errorCode로 분기한다.
 *
 * 위치가 common이 아닌 이유: 이 형태를 결정하는 것은 API 응답 규격이며,
 * 소비자가 웹 계열 실행 모듈(api/admin)뿐이다. batch는 사용하지 않는다.
 */
data class ErrorResponse(
    val errorCode: Int,
    val message: String,
) {
    companion object {
        fun of(errorCode: ErrorCode): ErrorResponse =
            ErrorResponse(errorCode.code, errorCode.message)
    }
}
