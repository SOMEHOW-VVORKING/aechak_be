package com.aechak.domain.sns.error

import com.aechak.common.error.ErrorCode

/** SNS 도메인 에러 코드 */
enum class SnsErrorCode(
    override val code: Int,
    override val message: String,
    override val status: Int,
) : ErrorCode {
    // SnsUserStats 130000번대. 없는 사용자, 탈퇴, 온보딩 전, 나를 차단한 사용자를 구분하지 않는다
    SNS_USER_UNAVAILABLE(130000, "볼 수 없는 계정입니다.", 404),
    SNS_USER_BLOCKED_BY_VIEWER(130001, "차단한 계정입니다.", 403),

    // Block 130100번대
    CANNOT_BLOCK_SELF(130100, "자기 자신을 차단할 수 없습니다.", 400),

    // Mute 130200번대
    CANNOT_MUTE_SELF(130200, "자기 자신을 뮤트할 수 없습니다.", 400),

    // Follow 130300번대
    CANNOT_FOLLOW_SELF(130300, "자기 자신을 팔로우할 수 없습니다.", 400),
}
