package com.aechak.api.user.user.response

import com.aechak.application.user.user.usecase.result.UserResult

/**
 * HTTP 응답 dto. Result → Response 변환은 companion의 from()으로 —
 * 프론트 계약 변경은 이 파일에서 흡수하고 application의 Result는 건드리지 않는다.
 */
data class UserResponse(
    val userId: Long,
    val nickname: String,
) {
    companion object {
        fun from(result: UserResult): UserResponse = UserResponse(result.userId, result.nickname)
    }
}
