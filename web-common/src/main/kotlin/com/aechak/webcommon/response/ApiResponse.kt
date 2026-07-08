package com.aechak.webcommon.response

/**
 * 성공 응답 래퍼. 데이터를 data 필드로 감싼다.
 *
 * 규칙:
 * - 성공 여부는 HTTP Status(2xx)로 판별. 본문에 code/status/timestamp 없음.
 * - 반환할 데이터가 없는 성공(생성/삭제)은 이 클래스를 쓰지 않고
 *   빈 본문 + HTTP Status(201/204)로만 표현한다.
 */
data class ApiResponse<T>(
    val data: T,
) {
    companion object {
        fun <T> of(data: T): ApiResponse<T> = ApiResponse(data)
    }
}
