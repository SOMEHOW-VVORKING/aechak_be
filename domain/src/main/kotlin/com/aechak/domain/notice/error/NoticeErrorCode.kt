package com.aechak.domain.notice.error

import com.aechak.common.error.ErrorCode

/** 공지 도메인 에러 코드 */
enum class NoticeErrorCode(
    override val code: Int,
    override val message: String,
    override val status: Int,
) : ErrorCode {
    NOTICE_NOT_FOUND(150000, "공지를 찾을 수 없습니다.", 404),
    NOTICE_TITLE_REQUIRED(150001, "공지 제목은 필수입니다.", 400),
    NOTICE_TITLE_TOO_LONG(150002, "공지 제목은 200자를 넘을 수 없습니다.", 400),
    NOTICE_CONTENT_REQUIRED(150003, "공지 내용은 필수입니다.", 400),
    NOTICE_CONTENT_TOO_LONG(150004, "공지 내용은 2000자를 넘을 수 없습니다.", 400),
    NOTICE_MEMO_TOO_LONG(150005, "내부 메모는 300자를 넘을 수 없습니다.", 400),
    NOTICE_ALREADY_PUBLISHED(150006, "이미 게시된 공지입니다.", 409),
    NOTICE_NOT_PUBLISHED(150007, "게시되지 않은 공지는 내릴 수 없습니다.", 409),
}
