package com.aechak.domain.sns.error

import com.aechak.common.error.ErrorCode

/** SNS 도메인 에러 코드 */
enum class SnsErrorCode(
    override val code: Int,
    override val message: String,
    override val status: Int,
) : ErrorCode {
    // SnsUserStats 130000번대. 없는 사용자, 탈퇴, 온보딩 전을 구분하지 않는다
    SNS_USER_UNAVAILABLE(130000, "볼 수 없는 계정입니다.", 404),

    // Block 130100번대
    CANNOT_BLOCK_SELF(130100, "자기 자신을 차단할 수 없습니다.", 400),

    // Mute 130200번대
    CANNOT_MUTE_SELF(130200, "자기 자신을 뮤트할 수 없습니다.", 400),
}
