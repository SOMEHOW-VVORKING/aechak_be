package com.aechak.domain.inquiry.error

import com.aechak.common.error.ErrorCode

/** 문의 도메인 에러 코드 */
enum class InquiryErrorCode(
    override val code: Int,
    override val message: String,
    override val status: Int,
) : ErrorCode {
    INQUIRY_CONTENT_REQUIRED(120000, "문의 내용은 필수입니다.", 400),
    INQUIRY_CONTENT_TOO_LONG(120001, "문의 내용은 2000자를 넘을 수 없습니다.", 400),
    INQUIRY_REPLY_EMAIL_REQUIRED(120002, "답변받을 이메일은 필수입니다.", 400),
    INQUIRY_REPLY_EMAIL_TOO_LONG(120003, "답변받을 이메일은 255자를 넘을 수 없습니다.", 400),
    INQUIRY_INVALID_REPLY_EMAIL(120004, "답변받을 이메일 형식이 올바르지 않습니다.", 400),
}
