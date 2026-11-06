package com.aechak.domain.sns.error

import com.aechak.common.error.ErrorCode

/** SNS 도메인 에러 코드 */
enum class SnsErrorCode(
    override val code: Int,
    override val message: String,
    override val status: Int,
) : ErrorCode {
    // 사용자 130000번대. 없는 사용자, 탈퇴, 온보딩 전, 나를 차단한 사용자를 구분하지 않는다
    SNS_USER_UNAVAILABLE(130000, "볼 수 없는 계정입니다.", 404),
    SNS_USER_BLOCKED_BY_VIEWER(130001, "차단한 계정입니다.", 403),

    // 차단 130100번대
    CANNOT_BLOCK_SELF(130100, "자기 자신을 차단할 수 없습니다.", 400),

    // 뮤트 130200번대
    CANNOT_MUTE_SELF(130200, "자기 자신을 뮤트할 수 없습니다.", 400),

    // 팔로우 130300번대
    CANNOT_FOLLOW_SELF(130300, "자기 자신을 팔로우할 수 없습니다.", 400),

    // 게시물 130400번대. 없는 게시물, 삭제, 탈퇴한 작성자, 나를 차단한 작성자를 구분하지 않는다
    SNS_POST_UNAVAILABLE(130400, "볼 수 없는 게시물입니다.", 404),
    SNS_POST_NOT_AUTHOR(130401, "본인 게시물만 수정하거나 삭제할 수 있습니다.", 403),
    SNS_POST_EMPTY(130402, "캡션이나 사진, 영상 중 하나는 있어야 합니다.", 400),
    SNS_POST_CAPTION_TOO_LONG(130403, "캡션은 2,200자까지 쓸 수 있습니다.", 400),
    SNS_POST_INVALID_MEDIA(130404, "첨부한 사진이나 영상을 확인해 주세요.", 400),
    SNS_POST_CAPTION_BLOCKED(130405, "부적절한 표현이 있어 게시할 수 없습니다.", 400),
    SNS_POST_TOO_MANY_HASHTAGS(130406, "해시태그는 30개까지 달 수 있습니다.", 400),
    SNS_POST_VIDEO_TOO_LONG(130407, "영상은 60초까지 올릴 수 있습니다.", 400),

    // 해시태그 130500번대
    SNS_HASHTAG_INVALID(130500, "해시태그에 쓸 수 없는 글자가 있습니다.", 400),
    SNS_HASHTAG_TOO_LONG(130501, "해시태그는 30자까지 쓸 수 있습니다.", 400),
    SNS_HASHTAG_BANNED_WORD(130502, "해시태그에 쓸 수 없는 단어가 있습니다.", 400),

    // 리포스트 130600번대
    SNS_REPOST_OWN_POST(130600, "본인 게시물은 리포스트할 수 없습니다.", 400),

    // 댓글 130700번대. 없는 댓글, 삭제, 차단 관계, 뮤트, 섀도우 처리를 구분하지 않는다
    SNS_COMMENT_UNAVAILABLE(130700, "볼 수 없는 댓글입니다.", 404),
    SNS_COMMENT_NOT_AUTHOR(130701, "본인 댓글만 수정하거나 삭제할 수 있습니다.", 403),
    SNS_COMMENT_EMPTY(130702, "댓글 내용을 입력해 주세요.", 400),
    SNS_COMMENT_TOO_LONG(130703, "댓글은 500자까지 쓸 수 있습니다.", 400),
    SNS_COMMENT_BLOCKED(130704, "부적절한 표현이 있어 댓글을 등록할 수 없습니다.", 400),
    SNS_COMMENT_REPLY_CLOSED(130705, "답글을 달 수 없는 댓글입니다.", 409),

    // 게시물 신고 130800번대
    SNS_POST_REPORT_SELF(130800, "본인 게시물은 신고할 수 없습니다.", 400),
    SNS_POST_REPORT_REASON_TEXT_REQUIRED(130801, "기타 사유로 신고하려면 상세 내용을 입력해 주세요.", 400),
    SNS_POST_ALREADY_REPORTED(130802, "이미 신고한 게시물입니다.", 409),
}
