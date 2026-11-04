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

    // Post 130400번대. 없는 게시물, 삭제, 탈퇴한 작성자, 나를 차단한 작성자를 구분하지 않는다
    SNS_POST_UNAVAILABLE(130400, "볼 수 없는 게시물입니다.", 404),
    SNS_POST_NOT_AUTHOR(130401, "본인 게시물만 수정하거나 삭제할 수 있습니다.", 403),
    SNS_POST_EMPTY(130402, "캡션이나 사진, 영상 중 하나는 있어야 합니다.", 400),
    SNS_POST_CAPTION_TOO_LONG(130403, "캡션은 2,200자까지 쓸 수 있습니다.", 400),
    SNS_POST_INVALID_MEDIA(130404, "첨부한 사진이나 영상을 확인해 주세요.", 400),
    SNS_POST_CAPTION_BLOCKED(130405, "부적절한 표현이 있어 게시할 수 없습니다.", 400),

    // Hashtag 130500번대
    SNS_HASHTAG_INVALID(130500, "해시태그에 쓸 수 없는 글자가 있습니다.", 400),
    SNS_HASHTAG_TOO_LONG(130501, "해시태그는 30자까지 쓸 수 있습니다.", 400),
    SNS_HASHTAG_BANNED_WORD(130502, "해시태그에 쓸 수 없는 단어가 있습니다.", 400),
}
